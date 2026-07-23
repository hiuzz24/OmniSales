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

        if (request.getVariants() != null && !request.getVariants().isEmpty()) {
            List<String> variantSkus = request.getVariants().stream()
                    .map(ProductVariantRequest::getSku)
                    .toList();
            if (!variantSkus.isEmpty()) {
                Set<String> uniqueSkus = new HashSet<>(variantSkus);
                if (uniqueSkus.size() < variantSkus.size()) {
                    throw new AppException(ErrorCode.VARIANT_SKU_CONFLICT);
                }
                if (productVariantRepository.existsBySkuInAndDeletedAtIsNull(variantSkus)) {
                    throw new AppException(ErrorCode.VARIANT_SKU_CONFLICT);
                }
                if (productRepository.existsBySkuInAndDeletedAtIsNull(variantSkus)) {
                    throw new AppException(ErrorCode.PRODUCT_SKU_CONFLICT);
                }
            }

            List<String> variantBarcodes = request.getVariants().stream()
                    .map(ProductVariantRequest::getBarcode)
                    .filter(barcode -> barcode != null && !barcode.trim().isEmpty())
                    .toList();
            if (!variantBarcodes.isEmpty()) {
                Set<String> uniqueBarcodes = new HashSet<>(variantBarcodes);
                if (uniqueBarcodes.size() < variantBarcodes.size()) {
                    throw new AppException(ErrorCode.VARIANT_BARCODE_CONFLICT);
                }
                if (productVariantRepository.existsByBarcodeInAndDeletedAtIsNull(variantBarcodes)) {
                    throw new AppException(ErrorCode.VARIANT_BARCODE_CONFLICT);
                }
            }
        }

        product.setCreatedBy(SecurityUtils.getCurrentUser().orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND)));

        Product savedProduct = productRepository.save(product);

        List<ProductVariant> savedVariants = new ArrayList<>();
        if (request.getVariants() != null && !request.getVariants().isEmpty()) {
            List<ProductVariant> productVariants = request.getVariants().stream()
                    .map(vr -> {
                        ProductVariant v = productVariantMapper.toEntity(vr);
                        v.setProduct(savedProduct);
                        applyCreateCostPriceDefault(v);
                        return v;
                    })
                    .toList();
            savedVariants = productVariantRepository.saveAll(productVariants);

            List<ProductImage> variantImages = new ArrayList<>();
            for (int i = 0; i < request.getVariants().size(); i++) {
                ProductVariantRequest variantRequest = request.getVariants().get(i);
                if (variantRequest.getImages() != null && !variantRequest.getImages().isEmpty()) {
                    long primaryCount = variantRequest.getImages().stream().filter(img -> Boolean.TRUE.equals(img.getIsPrimary())).count();
                    if (primaryCount > 1) {
                        throw new AppException(ErrorCode.INVALID_REQUEST);
                    }
                    ProductVariant savedVariant = savedVariants.get(i);
                    variantImages.addAll(variantRequest.getImages().stream()
                            .map(imgReq -> {
                                ProductImage img = productImageMapper.toEntity(imgReq);
                                img.setProduct(savedProduct);
                                img.setVariant(savedVariant);
                                return img;
                            })
                            .toList());
                }
            }
            if (!variantImages.isEmpty()) {
                productImageRepository.saveAll(variantImages);
            }
        }

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

        createInitialInventoryItems(savedVariants, request.getWarehouseId(), savedProduct.getLowStockThreshold());

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

        ProductResponse response = productMapper.toResponse(product);
        response.setHasOrders(orderItemRepository.existsByVariant_Product_Id(id));

        List<ProductImage> allImages = productImageRepository.findByProductIdOrderByIsPrimaryDescSortOrderAsc(id);

        List<ProductImageResponse> globalImageResponses = allImages.stream()
                .filter(img -> img.getVariant() == null)
                .map(productImageMapper::toResponse)
                .toList();
        response.setImages(globalImageResponses);

        List<ProductVariant> variants = productVariantRepository.findByProductIdAndDeletedAtIsNull(id);
        
        List<UUID> variantIds = variants.stream().map(ProductVariant::getId).toList();
        Map<UUID, StockSummaryDTO> stockMap = inventoryService.getStockSummary(variantIds);
        Map<UUID, List<String>> channelMap = channelService.getProductChannels(Collections.singletonList(id));
        Map<UUID, List<UUID>> channelIdMap = channelService.getProductChannelIds(Collections.singletonList(id));
        Map<UUID, List<ChannelSyncResponse>> channelSyncMap = channelService.getProductChannelSyncs(Collections.singletonList(id));

        Map<UUID, String> marketplaceSkuByVariantId = loadMarketplaceSkuByVariantId(variantIds);
        List<ProductVariantResponse> variantResponses = variants.stream().map(v -> {
            ProductVariantResponse vr = productVariantMapper.toResponse(v);
            String marketplaceSku = marketplaceSkuByVariantId.get(v.getId());
            if (marketplaceSku != null) {
                vr.setMarketplaceSku(marketplaceSku);
            }
            StockSummaryDTO stock = stockMap.get(v.getId());
            if (stock != null) {
                vr.setAvailableQuantity(stock.getAvailableQuantity());
                vr.setQuantityOnHand(stock.getQuantityOnHand());
            }
            List<ProductImageResponse> vImgResponses = allImages.stream()
                    .filter(img -> img.getVariant() != null && img.getVariant().getId().equals(v.getId()))
                    .map(productImageMapper::toResponse)
                    .toList();
            vr.setImages(vImgResponses);
            return vr;
        }).toList();

        response.setVariants(variantResponses);
        applyProductDisplaySku(response);
        response.setChannels(channelMap.getOrDefault(id, Collections.emptyList()));
        response.setChannelIds(channelIdMap.getOrDefault(id, Collections.emptyList()));
        response.setChannelSyncs(channelSyncMap.getOrDefault(id, Collections.emptyList()));

        return response;
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

        List<ProductResponse> products = buildProductResponses(productRepository.findAll(spec));
        products = aggregateProductResponsesBySku(products);
        products = filterProductResponsesByPlatforms(products, platforms);
        products = filterProductResponsesByKeyword(products, keyword);
        List<ProductResponse> pageContent = paginate(products, page, size);
        return PageResponse.<ProductResponse>builder()
                .content(pageContent)
                .page(page)
                .size(size)
                .totalElements(products.size())
                .totalProducts((long) products.size())
                .totalPages(totalPages(products.size(), size))
                .first(page <= 0)
                .last(page >= totalPages(products.size(), size) - 1)
                .build();
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


        List<ProductVariant> existingVariants = productVariantRepository.findByProductIdAndDeletedAtIsNull(id);
        Map<UUID, ProductVariant> existingVariantMap = existingVariants.stream()
                .collect(Collectors.toMap(ProductVariant::getId, v -> v));
        Map<UUID, VariantSyncSnapshot> existingVariantSnapshots = existingVariants.stream()
                .collect(Collectors.toMap(
                        ProductVariant::getId,
                        variant -> new VariantSyncSnapshot(variant.getSku(), variant.getPrice())
                ));

        if (request.getVariants() != null && !request.getVariants().isEmpty()) {
            List<String> variantSkus = request.getVariants().stream()
                    .map(ProductVariantRequest::getSku)
                    .toList();
            if (!variantSkus.isEmpty()) {
                Set<String> uniqueSkus = new HashSet<>(variantSkus);
                if (uniqueSkus.size() < variantSkus.size()) {
                    throw new AppException(ErrorCode.VARIANT_SKU_CONFLICT);
                }
                if (productVariantRepository.existsBySkuInAndProductIdNotAndDeletedAtIsNull(variantSkus, id)) {
                    throw new AppException(ErrorCode.VARIANT_SKU_CONFLICT);
                }
                if (productRepository.existsBySkuInAndIdNotAndDeletedAtIsNull(variantSkus, id)) {
                    throw new AppException(ErrorCode.PRODUCT_SKU_CONFLICT);
                }
            }

            List<String> variantBarcodes = request.getVariants().stream()
                    .map(ProductVariantRequest::getBarcode)
                    .filter(barcode -> barcode != null && !barcode.trim().isEmpty())
                    .toList();
            if (!variantBarcodes.isEmpty()) {
                Set<String> uniqueBarcodes = new HashSet<>(variantBarcodes);
                if (uniqueBarcodes.size() < variantBarcodes.size()) {
                    throw new AppException(ErrorCode.VARIANT_BARCODE_CONFLICT);
                }
                if (productVariantRepository.existsByBarcodeInAndProductIdNotAndDeletedAtIsNull(variantBarcodes, id)) {
                    throw new AppException(ErrorCode.VARIANT_BARCODE_CONFLICT);
                }
            }

            List<ProductVariant> updatedVariants = new ArrayList<>();
            List<ProductImage> variantImages = new ArrayList<>();
            Set<UUID> incomingVariantIds = new HashSet<>();

            for (ProductVariantRequest vr : request.getVariants()) {
                if (vr.getId() != null) {
                    incomingVariantIds.add(vr.getId());
                }
            }

            List<ProductVariant> variantsToDelete = existingVariants.stream()
                    .filter(v -> !incomingVariantIds.contains(v.getId()))
                    .toList();

            if (!variantsToDelete.isEmpty()) {
                List<UUID> variantIdsToDelete = variantsToDelete.stream().map(ProductVariant::getId).toList();
                List<UUID> variantIdsWithOrders = orderItemRepository.findVariantIdsWithOrders(variantIdsToDelete);

                for (ProductVariant v : variantsToDelete) {
                    if (variantIdsWithOrders.contains(v.getId())) {
                        v.setIsActive(false);
                    } else {
                        v.setDeletedAt(OffsetDateTime.now());
                    }
                }
                productVariantRepository.saveAll(variantsToDelete);
                productVariantRepository.flush();
            }

            if (request.getVariants() != null && !request.getVariants().isEmpty()) {
                for (ProductVariantRequest vr : request.getVariants()) {
                    ProductVariant matchingExisting = existingVariants.stream()
                            .filter(ev -> ev.getSku().equals(vr.getSku()))
                            .findFirst().orElse(null);
                    if (matchingExisting != null && !matchingExisting.getId().equals(vr.getId())) {
                        if (matchingExisting.getDeletedAt() == null) {
                            throw new AppException(ErrorCode.VARIANT_SKU_CONFLICT);
                        }
                    }
                }
            }

            for (int i = 0; i < request.getVariants().size(); i++) {
                ProductVariantRequest vr = request.getVariants().get(i);
                ProductVariant variant;
                BigDecimal existingPrice = null;
                BigDecimal existingCostPrice = null;
                boolean isNewVariant = vr.getId() == null;
                if (vr.getId() != null) {
                    if (existingVariantMap.containsKey(vr.getId())) {
                        variant = existingVariantMap.get(vr.getId());
                        existingPrice = variant.getPrice();
                        existingCostPrice = variant.getCostPrice();

                        if (!Objects.equals(variant.getSku(), vr.getSku()) && orderItemRepository.existsByVariant_Product_Id(id)) {
                            throw new AppException(ErrorCode.PRODUCT_HAS_ORDERS);
                        }
                    } else {
                        throw new AppException(ErrorCode.INVALID_REQUEST);
                    }
                } else {
                    variant = new ProductVariant();
                    variant.setProduct(product);
                    variant.setCreatedBy(product.getUpdatedBy());
                }

                productVariantMapper.updateEntityFromRequest(vr, variant);
                if (variant.getPrice() == null) {
                    variant.setPrice(isNewVariant ? BigDecimal.ZERO : existingPrice);
                }
                variant.setCostPrice(isNewVariant ? BigDecimal.ZERO : existingCostPrice);
                if (variant.getOptionValues() == null) {
                    variant.setOptionValues(new HashMap<>());
                }
                if (variant.getIsActive() == null) {
                    variant.setIsActive(true);
                }
                variant.setUpdatedBy(product.getUpdatedBy());

                updatedVariants.add(variant);
            }

            List<ProductVariant> savedVariantsList = productVariantRepository.saveAll(updatedVariants);
            markChangedVariantsOutOfSync(savedVariantsList, existingVariantSnapshots);

            for (int i = 0; i < request.getVariants().size(); i++) {
                ProductVariantRequest variantRequest = request.getVariants().get(i);
                if (variantRequest.getImages() != null && !variantRequest.getImages().isEmpty()) {
                    long primaryCount = variantRequest.getImages().stream().filter(img -> Boolean.TRUE.equals(img.getIsPrimary())).count();
                    if (primaryCount > 1) {
                        throw new AppException(ErrorCode.INVALID_REQUEST);
                    }
                    ProductVariant savedVariant = savedVariantsList.get(i);
                    variantImages.addAll(variantRequest.getImages().stream()
                            .map(imgReq -> {
                                ProductImage img = productImageMapper.toEntity(imgReq);
                                img.setProduct(product);
                                img.setVariant(savedVariant);
                                return img;
                            })
                            .toList());
                }
            }

            productImageRepository.deleteByProductId(id);
            productImageRepository.flush();
            if (!variantImages.isEmpty()) {
                productImageRepository.saveAll(variantImages);
            }


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

    private void markChangedVariantsOutOfSync(List<ProductVariant> savedVariants,
                                              Map<UUID, VariantSyncSnapshot> existingVariantSnapshots) {
        List<UUID> changedVariantIds = savedVariants.stream()
                .filter(variant -> variant.getId() != null)
                .filter(variant -> {
                    VariantSyncSnapshot existing = existingVariantSnapshots.get(variant.getId());
                    if (existing == null) {
                        return true;
                    }
                    return !Objects.equals(existing.sku(), variant.getSku())
                            || !sameAmount(existing.price(), variant.getPrice());
                })
                .map(ProductVariant::getId)
                .toList();
        if (changedVariantIds.isEmpty()) {
            return;
        }

        List<ChannelProductVariant> mappings =
                channelProductVariantRepository.findActiveByVariantIdInWithChannel(changedVariantIds);
        mappings.forEach(mapping -> mapping.setSyncStatus(SyncStatus.OUT_OF_SYNC));
        channelProductVariantRepository.saveAll(mappings);
    }

    private boolean sameAmount(BigDecimal first, BigDecimal second) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.compareTo(second) == 0;
    }

    private record VariantSyncSnapshot(String sku, BigDecimal price) {
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

    private PageResponse<ProductResponse> toPageResponse(Page<Product> pageResult, int page, int size) {
        List<ProductResponse> content = buildProductResponses(pageResult.getContent());

        return PageResponse.<ProductResponse>builder()
                .content(content)
                .page(page)
                .size(size)
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .first(pageResult.isFirst())
                .last(pageResult.isLast())
                .build();
    }

    private List<ProductResponse> buildProductResponses(List<Product> products) {
        List<ProductResponse> content = products.stream()
                .map(productMapper::toResponse)
                .toList();

        List<UUID> productIds = content.stream().map(ProductResponse::getId).toList();

        if (!productIds.isEmpty()) {
            List<ProductVariant> allVariants = productVariantRepository.findByProductIdInAndDeletedAtIsNull(productIds);
            List<ProductImage> allImages = productImageRepository.findByProductIdInOrderByIsPrimaryDescSortOrderAsc(productIds);

            List<UUID> allVariantIds = allVariants.stream().map(ProductVariant::getId).toList();
            Map<UUID, StockSummaryDTO> stockMap = inventoryService.getStockSummary(allVariantIds);
            Map<UUID, String> marketplaceSkuByVariantId = loadMarketplaceSkuByVariantId(allVariantIds);
            Map<UUID, List<String>> channelMap = channelService.getProductChannels(productIds);
            Map<UUID, List<UUID>> channelIdMap = channelService.getProductChannelIds(productIds);
            Map<UUID, List<ChannelSyncResponse>> channelSyncMap = channelService.getProductChannelSyncs(productIds);

            Map<UUID, List<ProductVariant>> variantsByProductId = allVariants.stream()
                    .collect(Collectors.groupingBy(v -> v.getProduct().getId()));

            Map<UUID, List<ProductImage>> imagesByProductId = allImages.stream()
                    .collect(Collectors.groupingBy(img -> img.getProduct().getId()));

            content.forEach(res -> {
                UUID pId = res.getId();

                List<ProductImage> pImages = imagesByProductId.getOrDefault(pId, Collections.emptyList());
                List<ProductImageResponse> globalImageResponses = pImages.stream()
                        .filter(img -> img.getVariant() == null)
                        .map(productImageMapper::toResponse)
                        .toList();
                res.setImages(globalImageResponses);

                List<ProductVariant> pVariants = variantsByProductId.getOrDefault(pId, Collections.emptyList());
                List<ProductVariantResponse> variantResponses = pVariants.stream().map(v -> {
                    ProductVariantResponse vr = productVariantMapper.toResponse(v);
                    String marketplaceSku = marketplaceSkuByVariantId.get(v.getId());
                    if (marketplaceSku != null) {
                        vr.setMarketplaceSku(marketplaceSku);
                    }
                    StockSummaryDTO stock = stockMap.get(v.getId());
                    if (stock != null) {
                        vr.setAvailableQuantity(stock.getAvailableQuantity());
                        vr.setQuantityOnHand(stock.getQuantityOnHand());
                    }
                    List<ProductImageResponse> vImgResponses = pImages.stream()
                            .filter(img -> img.getVariant() != null && img.getVariant().getId().equals(v.getId()))
                            .map(productImageMapper::toResponse)
                            .toList();
                    vr.setImages(vImgResponses);
                    return vr;
                }).toList();
                res.setVariants(variantResponses);
                applyProductDisplaySku(res);
                res.setChannels(channelMap.getOrDefault(pId, Collections.emptyList()));
                res.setChannelIds(channelIdMap.getOrDefault(pId, Collections.emptyList()));
                res.setChannelSyncs(channelSyncMap.getOrDefault(pId, Collections.emptyList()));
            });
        }

        return content;
    }

    private Map<UUID, String> loadMarketplaceSkuByVariantId(Collection<UUID> variantIds) {
        if (variantIds == null || variantIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> result = new HashMap<>();
        for (ChannelProductVariant mapping : channelProductVariantRepository.findActiveByVariantIdInWithChannel(new ArrayList<>(variantIds))) {
            if (mapping.getVariant() == null || mapping.getVariant().getId() == null) {
                continue;
            }
            String externalSku = normalizeSkuValue(mapping.getExternalSku());
            if (externalSku != null) {
                result.putIfAbsent(mapping.getVariant().getId(), externalSku);
            }
        }
        return result;
    }

    private void applyProductDisplaySku(ProductResponse product) {
        String marketplaceSku = product.getVariants() == null
                ? null
                : product.getVariants().stream()
                .map(ProductVariantResponse::getMarketplaceSku)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
        if (marketplaceSku != null) {
            product.setMarketplaceSku(marketplaceSku);
            product.setSku(marketplaceSku);
        }
        if (product.getProductIds() == null || product.getProductIds().isEmpty()) {
            product.setProductIds(product.getId() == null ? List.of() : List.of(product.getId()));
        }
    }

    private List<ProductResponse> aggregateProductResponsesBySku(List<ProductResponse> products) {
        if (products == null || products.isEmpty()) {
            return List.of();
        }

        int[] parents = new int[products.size()];
        for (int i = 0; i < parents.length; i++) {
            parents[i] = i;
        }

        Map<String, Integer> ownerBySku = new LinkedHashMap<>();
        for (int index = 0; index < products.size(); index++) {
            for (String key : productAggregateKeys(products.get(index))) {
                Integer owner = ownerBySku.putIfAbsent(key, index);
                if (owner != null) {
                    unionProductGroup(parents, owner, index);
                }
            }
        }

        Map<Integer, List<ProductResponse>> bySku = new LinkedHashMap<>();
        for (int index = 0; index < products.size(); index++) {
            bySku.computeIfAbsent(findProductGroup(parents, index), ignored -> new ArrayList<>())
                    .add(products.get(index));
        }

        List<ProductResponse> result = new ArrayList<>();
        for (List<ProductResponse> group : bySku.values()) {
            ProductResponse first = group.get(0);
            if (group.size() == 1) {
                result.add(first);
                continue;
            }

            LinkedHashSet<UUID> productIds = new LinkedHashSet<>();
            LinkedHashSet<UUID> channelIds = new LinkedHashSet<>();
            LinkedHashSet<String> channels = new LinkedHashSet<>();
            List<ChannelSyncResponse> channelSyncs = new ArrayList<>();
            List<ProductVariantResponse> variants = new ArrayList<>();
            List<ProductImageResponse> images = new ArrayList<>();

            for (ProductResponse product : group) {
                if (product.getId() != null) {
                    productIds.add(product.getId());
                }
                if (product.getProductIds() != null) {
                    productIds.addAll(product.getProductIds());
                }
                if (product.getChannelIds() != null) {
                    channelIds.addAll(product.getChannelIds());
                }
                if (product.getChannels() != null) {
                    channels.addAll(product.getChannels());
                }
                if (product.getChannelSyncs() != null) {
                    channelSyncs.addAll(product.getChannelSyncs());
                }
                if (product.getVariants() != null) {
                    variants.addAll(product.getVariants());
                }
                if (product.getImages() != null) {
                    images.addAll(product.getImages());
                }
            }

            first.setProductIds(new ArrayList<>(productIds));
            first.setChannels(new ArrayList<>(channels));
            first.setChannelIds(new ArrayList<>(channelIds));
            first.setChannelSyncs(distinctChannelSyncs(channelSyncs));
            first.setVariants(aggregateProductVariantsBySku(variants));
            if ((first.getImages() == null || first.getImages().isEmpty()) && !images.isEmpty()) {
                first.setImages(images);
            }
            applyProductDisplaySku(first);
            result.add(first);
        }
        return result;
    }

    private int findProductGroup(int[] parents, int index) {
        if (parents[index] != index) {
            parents[index] = findProductGroup(parents, parents[index]);
        }
        return parents[index];
    }

    private void unionProductGroup(int[] parents, int left, int right) {
        int leftRoot = findProductGroup(parents, left);
        int rightRoot = findProductGroup(parents, right);
        if (leftRoot != rightRoot) {
            parents[rightRoot] = leftRoot;
        }
    }

    private List<ProductVariantResponse> aggregateProductVariantsBySku(List<ProductVariantResponse> variants) {
        if (variants == null || variants.isEmpty()) {
            return List.of();
        }

        Map<String, List<ProductVariantResponse>> bySku = variants.stream()
                .collect(Collectors.groupingBy(
                        this::variantAggregateKey,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<ProductVariantResponse> result = new ArrayList<>();
        for (List<ProductVariantResponse> group : bySku.values()) {
            ProductVariantResponse first = group.get(0);
            ProductVariantResponse stockSource = group.stream()
                    .max(Comparator
                            .comparingInt((ProductVariantResponse item) -> safeInt(item.getQuantityOnHand()))
                            .thenComparingInt(item -> safeInt(item.getAvailableQuantity())))
                    .orElse(first);
            int availableQuantity = safeInt(stockSource.getAvailableQuantity());
            int quantityOnHand = safeInt(stockSource.getQuantityOnHand());
            BigDecimal price = group.stream()
                    .map(ProductVariantResponse::getPrice)
                    .filter(value -> value != null && value.compareTo(BigDecimal.ZERO) > 0)
                    .findFirst()
                    .orElse(first.getPrice());
            String marketplaceSku = firstNonBlank(first.getMarketplaceSku(), first.getSku());
            first.setSku(marketplaceSku);
            first.setMarketplaceSku(marketplaceSku);
            first.setPrice(price);
            first.setAvailableQuantity(availableQuantity);
            first.setQuantityOnHand(quantityOnHand);
            result.add(first);
        }
        return result;
    }

    private List<ChannelSyncResponse> distinctChannelSyncs(List<ChannelSyncResponse> syncs) {
        Map<UUID, ChannelSyncResponse> byChannelId = new LinkedHashMap<>();
        for (ChannelSyncResponse sync : syncs) {
            if (sync == null || sync.getChannelId() == null) {
                continue;
            }
            byChannelId.putIfAbsent(sync.getChannelId(), sync);
        }
        return new ArrayList<>(byChannelId.values());
    }

    private List<ProductResponse> filterProductResponsesByKeyword(List<ProductResponse> products, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return products;
        }
        String needle = keyword.trim().toLowerCase(Locale.ROOT);
        return products.stream()
                .filter(product -> containsText(product.getName(), needle)
                        || containsText(product.getSku(), needle)
                        || containsText(product.getMarketplaceSku(), needle)
                        || (product.getVariants() != null && product.getVariants().stream().anyMatch(variant ->
                        containsText(variant.getSku(), needle)
                                || containsText(variant.getMarketplaceSku(), needle)
                                || containsText(variant.getName(), needle))))
                .toList();
    }

    private List<ProductResponse> filterProductResponsesByPlatforms(List<ProductResponse> products, Collection<PlatformType> requiredPlatforms) {
        if (requiredPlatforms == null || requiredPlatforms.isEmpty() || products == null || products.isEmpty()) {
            return products;
        }
        Set<String> required = requiredPlatforms.stream()
                .filter(Objects::nonNull)
                .map(platform -> platform.name().toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (required.isEmpty()) {
            return products;
        }
        return products.stream()
                .filter(product -> productPlatforms(product).containsAll(required))
                .toList();
    }

    private Set<String> productPlatforms(ProductResponse product) {
        LinkedHashSet<String> platforms = new LinkedHashSet<>();
        if (product.getChannels() != null) {
            product.getChannels().stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(value -> value.trim().toUpperCase(Locale.ROOT))
                    .forEach(platforms::add);
        }
        if (product.getChannelSyncs() != null) {
            product.getChannelSyncs().stream()
                    .map(ChannelSyncResponse::getPlatform)
                    .filter(value -> value != null && !value.isBlank())
                    .map(value -> value.trim().toUpperCase(Locale.ROOT))
                    .forEach(platforms::add);
        }
        return platforms;
    }

    private Set<String> productAggregateKeys(ProductResponse product) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        addProductSkuKey(keys, product.getMarketplaceSku());
        addProductSkuKey(keys, product.getSku());
        if (product.getVariants() != null) {
            for (ProductVariantResponse variant : product.getVariants()) {
                addProductSkuKey(keys, variant.getMarketplaceSku());
                addProductSkuKey(keys, variant.getSku());
            }
        }
        if (keys.isEmpty()) {
            keys.add(product.getId() == null ? UUID.randomUUID().toString() : "product:" + product.getId());
        }
        return keys;
    }

    private void addProductSkuKey(Set<String> keys, String sku) {
        String normalized = normalizeSkuKey(sku);
        if (!normalized.isBlank()) {
            keys.add("sku:" + normalized);
        }
    }

    private String variantAggregateKey(ProductVariantResponse variant) {
        String sku = normalizeSkuKey(firstNonBlank(variant.getMarketplaceSku(), variant.getSku()));
        if (!sku.isBlank()) {
            return "sku:" + sku;
        }
        return variant.getId() == null ? UUID.randomUUID().toString() : "variant:" + variant.getId();
    }

    private boolean containsText(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private String normalizeSkuValue(String sku) {
        if (sku == null || sku.isBlank()) {
            return null;
        }
        return sku.trim();
    }

    private String normalizeSkuKey(String sku) {
        return sku == null ? "" : sku.trim().toLowerCase(Locale.ROOT);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private List<ProductResponse> paginate(List<ProductResponse> source, int page, int size) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        int safeSize = Math.max(size, 1);
        int from = Math.min(Math.max(page, 0) * safeSize, source.size());
        int to = Math.min(from + safeSize, source.size());
        return source.subList(from, to);
    }

    private int totalPages(int totalElements, int size) {
        int safeSize = Math.max(size, 1);
        return (int) Math.ceil((double) totalElements / safeSize);
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

    private void createInitialInventoryItems(List<ProductVariant> variants, UUID warehouseId, Integer lowStockThreshold) {
        if (warehouseId == null || variants == null || variants.isEmpty()) {
            return;
        }

        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .filter(w -> w.getDeletedAt() == null)
                .filter(w -> Boolean.TRUE.equals(w.getIsActive()))
                .orElseThrow(() -> new AppException(ErrorCode.WAREHOUSE_NOT_FOUND));

        List<InventoryItem> itemsToCreate = new ArrayList<>();
        int threshold = lowStockThreshold == null ? 5 : lowStockThreshold;
        for (ProductVariant variant : variants) {
            if (variant.getId() == null) {
                continue;
            }
            boolean exists = inventoryItemRepository
                    .findByWarehouseIdAndVariantId(warehouse.getId(), variant.getId())
                    .isPresent();
            if (exists) {
                continue;
            }

            itemsToCreate.add(InventoryItem.builder()
                    .warehouse(warehouse)
                    .variant(variant)
                    .quantityOnHand(0)
                    .reservedQuantity(0)
                    .averageCost(variant.getCostPrice() == null ? BigDecimal.ZERO : variant.getCostPrice())
                    .lowStockThreshold(threshold)
                    .build());
        }

        if (!itemsToCreate.isEmpty()) {
            inventoryItemRepository.saveAll(itemsToCreate);
        }
    }

    private void applyCreateCostPriceDefault(ProductVariant variant) {
        variant.setCostPrice(BigDecimal.ZERO);
    }
}
