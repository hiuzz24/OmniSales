package fu.osms.catalog.service.impl;

import fu.osms.catalog.dto.request.ProductRequest;
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
import fu.osms.inventory.service.InventoryService;
import fu.osms.catalog.service.ProductService;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelProduct;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.channel.repository.ChannelProductRepository;
import fu.osms.channel.service.ChannelService;
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
    private final ChannelRepository channelRepository;
    private final ChannelProductRepository channelProductRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductLogRepository productLogRepository;
    private final ProductSyncOrchestratorService productSyncOrchestratorService;

    @Override
    @Transactional
    public ProductResponse create(ProductRequest request) {
        if(productRepository.existsBySkuAndDeletedAtIsNull(request.getSku())){
            throw new AppException(ErrorCode.PRODUCT_SKU_CONFLICT);
        }

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

        if (request.getVariants() != null && !request.getVariants().isEmpty()) {
            List<ProductVariant> productVariants = request.getVariants().stream()
                    .map(vr -> {
                        ProductVariant v = productVariantMapper.toEntity(vr);
                        v.setProduct(savedProduct);
                        return v;
                    })
                    .toList();
            List<ProductVariant> savedVariants = productVariantRepository.saveAll(productVariants);

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

        if (request.getChannelIds() != null && !request.getChannelIds().isEmpty()) {
            List<Channel> channels = channelRepository.findAllById(request.getChannelIds());
            List<ChannelProduct> channelProducts = channels.stream()
                    .map(channel -> ChannelProduct.builder()
                            .channel(channel)
                            .product(savedProduct)
                            .mappingState("ACTIVE")
                            .syncStatus(SyncStatus.PENDING)
                            .build())
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

        List<ProductImage> allImages = productImageRepository.findByProductIdOrderBySortOrderAsc(id);

        List<ProductImageResponse> globalImageResponses = allImages.stream()
                .filter(img -> img.getVariant() == null)
                .map(productImageMapper::toResponse)
                .toList();
        response.setImages(globalImageResponses);

        List<ProductVariant> variants = productVariantRepository.findByProductIdAndDeletedAtIsNull(id);
        
        List<UUID> variantIds = variants.stream().map(ProductVariant::getId).toList();
        Map<UUID, StockSummaryDTO> stockMap = inventoryService.getStockSummary(variantIds);
        Map<UUID, List<String>> channelMap = channelService.getProductChannels(Collections.singletonList(id));
        Map<UUID, List<ChannelSyncResponse>> channelSyncMap = channelService.getProductChannelSyncs(Collections.singletonList(id));

        List<ProductVariantResponse> variantResponses = variants.stream().map(v -> {
            ProductVariantResponse vr = productVariantMapper.toResponse(v);
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
        response.setChannels(channelMap.getOrDefault(id, Collections.emptyList()));
        response.setChannelSyncs(channelSyncMap.getOrDefault(id, Collections.emptyList()));

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(String keyword, ProductStatus status, PlatformType platform, int page, int size) {
        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));
            
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            
            if (keyword != null && !keyword.trim().isEmpty()) {
                String likePattern = "%" + keyword.toLowerCase() + "%";
                Predicate nameLike = cb.like(cb.lower(root.get("name")), likePattern);
                Predicate skuLike = cb.like(cb.lower(root.get("sku")), likePattern);
                predicates.add(cb.or(nameLike, skuLike));
            }

            if (platform != null) {
                Subquery<UUID> subquery = query.subquery(UUID.class);
                Root<ChannelProduct> cpRoot = subquery.from(ChannelProduct.class);
                Join<ChannelProduct, Channel> channelJoin = cpRoot.join("channel");
                subquery.select(cpRoot.get("product").get("id"));
                subquery.where(
                        cb.equal(channelJoin.get("platform"), platform),
                        cb.equal(cpRoot.get("mappingState"), "ACTIVE")
                );
                predicates.add(root.get("id").in(subquery));
            }
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Product> pageResult = productRepository.findAll(spec, PageRequest.of(page, size));
        return toPageResponse(pageResult, page, size);
    }

    @Override
    @Transactional
    public ProductResponse update(UUID id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

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
                if (vr.getId() != null) {
                    if (existingVariantMap.containsKey(vr.getId())) {
                        variant = existingVariantMap.get(vr.getId());

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
                    for (Channel channel : channels) {
                        ChannelProduct existingCp = existingChannelProductMap.get(channel.getId());
                        if (existingCp != null) {
                            if (!"ACTIVE".equals(existingCp.getMappingState())) {
                                existingCp.setMappingState("ACTIVE");
                            }
                            channelsToSave.add(existingCp);
                            existingChannelProductMap.remove(channel.getId());
                        } else {
                            channelsToSave.add(ChannelProduct.builder()
                                    .channel(channel)
                                    .product(product)
                                    .mappingState("ACTIVE")
                                    .syncStatus(SyncStatus.PENDING)
                                    .build());
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

    private PageResponse<ProductResponse> toPageResponse(Page<Product> pageResult, int page, int size) {
        List<ProductResponse> content = pageResult.getContent().stream()
                .map(productMapper::toResponse)
                .toList();

        List<UUID> productIds = content.stream().map(ProductResponse::getId).toList();

        if (!productIds.isEmpty()) {
            List<ProductVariant> allVariants = productVariantRepository.findByProductIdInAndDeletedAtIsNull(productIds);
            List<ProductImage> allImages = productImageRepository.findByProductIdInOrderBySortOrderAsc(productIds);

            List<UUID> allVariantIds = allVariants.stream().map(ProductVariant::getId).toList();
            Map<UUID, StockSummaryDTO> stockMap = inventoryService.getStockSummary(allVariantIds);
            Map<UUID, List<String>> channelMap = channelService.getProductChannels(productIds);
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
                res.setChannels(channelMap.getOrDefault(pId, Collections.emptyList()));
                res.setChannelSyncs(channelSyncMap.getOrDefault(pId, Collections.emptyList()));
            });
        }

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
