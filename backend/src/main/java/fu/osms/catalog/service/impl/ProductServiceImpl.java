package fu.osms.catalog.service.impl;

import fu.osms.catalog.dto.request.ProductRequest;
import fu.osms.catalog.dto.request.ChannelConfigRequest;
import fu.osms.catalog.dto.request.ProductVariantRequest;
import fu.osms.catalog.dto.response.ProductResponse;
import fu.osms.catalog.entity.Category;
import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductImage;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.enums.ProductStatus;
import fu.osms.catalog.mapper.ProductMapper;
import fu.osms.catalog.mapper.ProductVariantMapper;
import fu.osms.catalog.repository.CategoryRepository;
import fu.osms.catalog.repository.ProductRepository;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.catalog.repository.ProductImageRepository;
import fu.osms.catalog.mapper.ProductImageMapper;
import fu.osms.catalog.dto.response.ProductImageResponse;
import fu.osms.catalog.dto.response.ProductVariantResponse;
import fu.osms.channel.dto.response.ChannelSyncResponse;
import fu.osms.catalog.entity.ProductLog;
import fu.osms.catalog.enums.ProductLogAction;
import fu.osms.catalog.repository.ProductLogRepository;
import fu.osms.auth.entity.User;
import fu.osms.inventory.dto.response.StockSummaryDTO;
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.repository.WarehouseRepository;
import fu.osms.inventory.service.InventoryService;
import fu.osms.catalog.service.ProductService;
import fu.osms.catalog.service.ProductChannelConfigService;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelProduct;
import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.channel.repository.ChannelProductRepository;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.channel.service.ChannelService;
import fu.osms.channel.service.ChannelConnectionValidator;
import fu.osms.order.repository.OrderItemRepository;
import fu.osms.common.dto.PageResponse;
import fu.osms.common.enums.SyncStatus;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.utils.SecurityUtils;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import fu.osms.sync.dto.SyncResult;
import fu.osms.sync.service.ProductSyncOrchestratorService;
import fu.osms.messaging.constants.RabbitMQConstants;
import fu.osms.messaging.dto.ProductSyncMessage;
import fu.osms.messaging.publisher.EventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;
    private final ProductVariantMapper productVariantMapper;
    private final ProductImageMapper productImageMapper;
    private final ProductVariantRepository productVariantRepository;
    private final ProductImageRepository productImageRepository;
    private final InventoryService inventoryService;
    private final ChannelService channelService;
    private final ChannelConnectionValidator channelConnectionValidator;
    private final ChannelRepository channelRepository;
    private final ChannelProductRepository channelProductRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductLogRepository productLogRepository;
    private final ProductSyncOrchestratorService productSyncOrchestratorService;
    private final ProductChannelConfigService productChannelConfigService;
    private final EventPublisher eventPublisher;
    private final WarehouseRepository warehouseRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final ChannelProductVariantRepository channelProductVariantRepository;

    @Override
    @Transactional
    public ProductResponse create(ProductRequest request) {
        if(productRepository.existsBySkuAndDeletedAtIsNull(request.getSku())){
            throw new AppException(ErrorCode.PRODUCT_SKU_CONFLICT);
        }
        Map<UUID, ChannelConfigRequest> requestedChannelConfigs = channelConfigsById(request);

        Product product = productMapper.toEntity(request);

        if(request.getAttributes() == null){
            product.setAttributes(new HashMap<>());
}
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));

        product.setCategory(category);

        variantMutationService().validateCreate(request.getVariants());

        product.setCreatedBy(SecurityUtils.getCurrentUser().orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND)));

        Product savedProduct = productRepository.save(product);

        List<ProductVariant> savedVariants = variantMutationService().create(savedProduct, request.getVariants());

        if (request.getImages() != null && !request.getImages().isEmpty()) {
            long primaryCount = request.getImages().stream().filter(img -> Boolean.TRUE.equals(img.getIsPrimary())).count();
            if (primaryCount > 1) {
                throw new AppException(ErrorCode.INVALID_REQUEST);
            }
            List<ProductImage> productImages = request.getImages().stream()
                    .map(imgReq -> {
                        ProductImage img = productImageMapper.toEntity(imgReq);
                        img.setProduct(savedProduct);
                        return img;
                    })
                    .toList();
            productImageRepository.saveAll(productImages);
        }

        inventoryInitializer().createInitialInventoryItems(
                savedVariants,
                request.getWarehouseId(),
                savedProduct.getLowStockThreshold()
        );

        if (request.getChannelIds() != null && !request.getChannelIds().isEmpty()) {
            List<Channel> channels = channelRepository.findAllById(request.getChannelIds());
            channels.forEach(channelConnectionValidator::validateConnected);
            List<ChannelProduct> channelProducts = channels.stream()
                    .map(channel -> {
                        ChannelProduct mapping = ChannelProduct.builder()
                                .channel(channel)
                                .product(savedProduct)
                                .mappingState("ACTIVE")
                                .syncStatus(SyncStatus.PENDING)
                                .build();
                        productChannelConfigService.applyInitialConfig(mapping, requestedChannelConfigs.get(channel.getId()));
                        return mapping;
                    })
                    .toList();
            if (!channelProducts.isEmpty()) {
                channelProductRepository.saveAll(channelProducts);
            }
        }

        logProductAction(
                savedProduct,
                null,
                ProductLogAction.CREATE,
                Map.of("message", "Tạo mới sản phẩm"),
                savedProduct.getCreatedBy(),
                null,
                null,
                "Tạo mới sản phẩm"
        );

        return this.getById(savedProduct.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getById(UUID id) {
        Product product = productRepository.findById(id)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        return responseAssembler().assemble(product);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(String keyword, ProductStatus status, Collection<PlatformType> platforms, int page, int size) {
        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));

            Subquery<UUID> variantExists = query.subquery(UUID.class);
            Root<ProductVariant> variantRoot = variantExists.from(ProductVariant.class);
            variantExists.select(variantRoot.get("id"));
            variantExists.where(
                    cb.equal(variantRoot.get("product"), root),
                    cb.isNull(variantRoot.get("deletedAt"))
            );
            predicates.add(cb.exists(variantExists));
            
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return responseAssembler().search(productRepository.findAll(spec), keyword, platforms, page, size);
    }

    @Override
    @Transactional
    public ProductResponse update(UUID id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        Map<UUID, ChannelConfigRequest> requestedChannelConfigs = channelConfigsById(request);

        if (request.getVersion() != null && !Objects.equals(product.getVersion(), request.getVersion())) {
            throw new AppException(ErrorCode.CONCURRENT_UPDATE);
        }

        if (!Objects.equals(product.getSku(), request.getSku())) {
            if (orderItemRepository.existsByVariant_Product_Id(id)) {
                throw new AppException(ErrorCode.PRODUCT_HAS_ORDERS);
            }
            if (productRepository.existsBySkuAndIdNotAndDeletedAtIsNull(request.getSku(), id)) {
                throw new AppException(ErrorCode.PRODUCT_SKU_CONFLICT);
            }
        }

        productMapper.updateEntityFromRequest(request, product);
        if (product.getAttributes() == null) {
            product.setAttributes(new HashMap<>());
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
        product.setCategory(category);
        product.setUpdatedBy(SecurityUtils.getCurrentUser().orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND)));


        if (request.getVariants() != null && !request.getVariants().isEmpty()) {
            variantMutationService().update(product, request.getVariants());

            if (request.getImages() != null && !request.getImages().isEmpty()) {
                long primaryCount = request.getImages().stream().filter(img -> Boolean.TRUE.equals(img.getIsPrimary())).count();
                if (primaryCount > 1) {
                    throw new AppException(ErrorCode.INVALID_REQUEST);
                }
                List<ProductImage> productImages = request.getImages().stream()
                        .map(imgReq -> {
                            ProductImage img = productImageMapper.toEntity(imgReq);
                            img.setProduct(product);
                            return img;
                        })
                        .toList();
                productImageRepository.saveAll(productImages);
            }

            if (request.getChannelIds() != null) {
                List<ChannelProduct> currentChannels = channelProductRepository.findByProductId(id);
                Map<UUID, ChannelProduct> existingChannelProductMap = currentChannels.stream()
                        .collect(Collectors.toMap(cp -> cp.getChannel().getId(), cp -> cp));

                List<ChannelProduct> channelsToSave = new ArrayList<>();
                Set<UUID> incomingChannelIds = new HashSet<>(request.getChannelIds());

                if (!incomingChannelIds.isEmpty()) {
                    List<Channel> channels = channelRepository.findAllById(incomingChannelIds);
                    channels.forEach(channelConnectionValidator::validateConnected);
                    for (Channel channel : channels) {
                        ChannelProduct existingCp = existingChannelProductMap.get(channel.getId());
                        if (existingCp != null) {
                            if (!"ACTIVE".equals(existingCp.getMappingState())) {
                                existingCp.setMappingState("ACTIVE");
                            }
                            ChannelConfigRequest config = requestedChannelConfigs.get(channel.getId());
                            if (config != null) {
                                productChannelConfigService.applyInitialConfig(existingCp, config);
                            }
                            channelsToSave.add(existingCp);
                            existingChannelProductMap.remove(channel.getId());
                        } else {
                            ChannelProduct newMapping = ChannelProduct.builder()
                                    .channel(channel)
                                    .product(product)
                                    .mappingState("ACTIVE")
                                    .syncStatus(SyncStatus.PENDING)
                                    .build();
                            productChannelConfigService.applyInitialConfig(newMapping, requestedChannelConfigs.get(channel.getId()));
                            channelsToSave.add(newMapping);
                        }
                    }
                }

                for (ChannelProduct remainingCp : existingChannelProductMap.values()) {
                    if (!"ARCHIVED".equals(remainingCp.getMappingState())) {
                        remainingCp.setMappingState("ARCHIVED");
                        channelsToSave.add(remainingCp);
                    }
                }

                if (!channelsToSave.isEmpty()) {
                    channelProductRepository.saveAll(channelsToSave);
                }
            }
        }

        logProductAction(
                product,
                null,
                ProductLogAction.UPDATE,
                Map.of("message", "Cập nhật sản phẩm"),
                product.getUpdatedBy(),
                null,
                null,
                "Cập nhật thông tin sản phẩm"
        );

        return this.getById(product.getId());
    }

    private Map<UUID, ChannelConfigRequest> channelConfigsById(ProductRequest request) {
        if (request.getChannelConfigs() == null || request.getChannelConfigs().isEmpty()) {
            return Collections.emptyMap();
        }
        Set<UUID> selectedChannelIds = request.getChannelIds() == null
                ? Collections.emptySet()
                : new HashSet<>(request.getChannelIds());
        Map<UUID, ChannelConfigRequest> result = new HashMap<>();
        for (ChannelConfigRequest config : request.getChannelConfigs()) {
            if (config == null || config.getChannelId() == null || !selectedChannelIds.contains(config.getChannelId())) {
                throw new AppException(ErrorCode.INVALID_REQUEST, "Channel configuration must belong to a selected channel");
            }
            if (result.put(config.getChannelId(), config) != null) {
                throw new AppException(ErrorCode.INVALID_REQUEST, "Duplicate channel configuration");
            }
        }
        return result;
    }

    @Override
    @Transactional
    public ProductResponse updateStatus(UUID id, ProductStatus status) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Product product = productRepository.findById(id)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        logProductAction(
                product,
                null,
                ProductLogAction.DELETE,
                Map.of("message", "Xóa sản phẩm"),
                SecurityUtils.getCurrentUser().orElse(null),
                null,
                null,
                "Xóa sản phẩm"
        );

        product.setDeletedAt(OffsetDateTime.now());
        productRepository.save(product);

        List<ProductVariant> variants = productVariantRepository.findByProductIdAndDeletedAtIsNull(id);
        if (!variants.isEmpty()) {
            variants.forEach(v -> v.setDeletedAt(OffsetDateTime.now()));
            productVariantRepository.saveAll(variants);
        }
    }


    @Override
    public SyncResult syncProductToAllChannels(UUID productId) {
        return productSyncOrchestratorService.syncProductToAllChannels(productId);
    }

    @Override
    public SyncResult syncProductToChannel(UUID productId, UUID channelId) {
        return productSyncOrchestratorService.syncProductToChannel(productId, channelId);
    }

    @Override
    public void syncProductToAllChannelsAsync(UUID productId) {
        eventPublisher.publish(
                RabbitMQConstants.PRODUCT_SYNC_PUSH,
                new ProductSyncMessage(productId, null),
                () -> productSyncOrchestratorService.syncProductToAllChannels(productId));
    }

    @Override
    public void syncProductToChannelAsync(UUID productId, UUID channelId) {
        eventPublisher.publish(
                RabbitMQConstants.PRODUCT_SYNC_PUSH,
                new ProductSyncMessage(productId, channelId),
                () -> productSyncOrchestratorService.syncProductToChannel(productId, channelId));
    }

    private ProductInventoryInitializer inventoryInitializer() {
        return new ProductInventoryInitializer(warehouseRepository, inventoryItemRepository);
    }

    private ProductVariantMutationService variantMutationService() {
        return new ProductVariantMutationService(
                productRepository,
                productVariantRepository,
                productImageRepository,
                productVariantMapper,
                productImageMapper,
                orderItemRepository,
                channelProductVariantRepository,
                inventoryInitializer()
        );
    }

    private ProductResponseAssembler responseAssembler() {
        return new ProductResponseAssembler(
                productMapper,
                productVariantMapper,
                productImageMapper,
                productVariantRepository,
                productImageRepository,
                inventoryService,
                channelService,
                orderItemRepository,
                channelProductVariantRepository
        );
    }

    private void logProductAction(Product product, ProductVariant variant, ProductLogAction action, Map<String, Object> fieldChanges, User performedBy, String referenceType, UUID referenceId, String notes) {
        ProductLog productLog = ProductLog.builder()
                .product(product)
                .variant(variant)
                .sku(variant != null ? variant.getSku() : product.getSku())
                .action(action)
                .fieldChanges(fieldChanges != null ? fieldChanges : new HashMap<>())
                .performedBy(performedBy)
                .performedByEmail(performedBy != null ? performedBy.getEmail() : null)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .notes(notes)
                .build();
        productLogRepository.save(productLog);
    }

}
