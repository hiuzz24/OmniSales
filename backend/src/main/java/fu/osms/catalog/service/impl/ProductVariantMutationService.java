package fu.osms.catalog.service.impl;

import fu.osms.catalog.dto.request.ProductVariantRequest;
import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductImage;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.mapper.ProductImageMapper;
import fu.osms.catalog.mapper.ProductVariantMapper;
import fu.osms.catalog.repository.ProductImageRepository;
import fu.osms.catalog.repository.ProductRepository;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.common.enums.SyncStatus;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.order.repository.OrderItemRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

final class ProductVariantMutationService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductVariantMapper productVariantMapper;
    private final ProductImageMapper productImageMapper;
    private final OrderItemRepository orderItemRepository;
    private final ChannelProductVariantRepository channelProductVariantRepository;
    private final ProductInventoryInitializer inventoryInitializer;

    ProductVariantMutationService(ProductRepository productRepository,
                                  ProductVariantRepository productVariantRepository,
                                  ProductImageRepository productImageRepository,
                                  ProductVariantMapper productVariantMapper,
                                  ProductImageMapper productImageMapper,
                                  OrderItemRepository orderItemRepository,
                                  ChannelProductVariantRepository channelProductVariantRepository,
                                  ProductInventoryInitializer inventoryInitializer) {
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.productImageRepository = productImageRepository;
        this.productVariantMapper = productVariantMapper;
        this.productImageMapper = productImageMapper;
        this.orderItemRepository = orderItemRepository;
        this.channelProductVariantRepository = channelProductVariantRepository;
        this.inventoryInitializer = inventoryInitializer;
    }

    void validateCreate(List<ProductVariantRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        List<String> variantSkus = requests.stream().map(ProductVariantRequest::getSku).toList();
        if (!variantSkus.isEmpty()) {
            if (new HashSet<>(variantSkus).size() < variantSkus.size()) {
                throw new AppException(ErrorCode.VARIANT_SKU_CONFLICT);
            }
            if (productVariantRepository.existsBySkuInAndDeletedAtIsNull(variantSkus)) {
                throw new AppException(ErrorCode.VARIANT_SKU_CONFLICT);
            }
            if (productRepository.existsBySkuInAndDeletedAtIsNull(variantSkus)) {
                throw new AppException(ErrorCode.PRODUCT_SKU_CONFLICT);
            }
        }
        validateCreateBarcodes(requests);
    }

    List<ProductVariant> create(Product product, List<ProductVariantRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return new ArrayList<>();
        }
        List<ProductVariant> variants = requests.stream()
                .map(request -> {
                    ProductVariant variant = productVariantMapper.toEntity(request);
                    variant.setProduct(product);
                    inventoryInitializer.applyCreateCostPriceDefault(variant);
                    return variant;
                })
                .toList();
        List<ProductVariant> savedVariants = productVariantRepository.saveAll(variants);
        saveVariantImages(product, requests, savedVariants);
        return savedVariants;
    }

    void update(Product product, List<ProductVariantRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        UUID productId = product.getId();
        List<ProductVariant> existingVariants = productVariantRepository.findByProductIdAndDeletedAtIsNull(productId);
        Map<UUID, ProductVariant> existingById = existingVariants.stream()
                .collect(Collectors.toMap(ProductVariant::getId, variant -> variant));
        Map<UUID, VariantSyncSnapshot> snapshots = existingVariants.stream()
                .collect(Collectors.toMap(ProductVariant::getId,
                        variant -> new VariantSyncSnapshot(variant.getSku(), variant.getPrice())));

        validateUpdate(productId, requests);
        Set<UUID> incomingIds = requests.stream()
                .map(ProductVariantRequest::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        deleteMissingVariants(productId, existingVariants, incomingIds);
        validateExistingSkuIdentity(existingVariants, requests);

        List<ProductVariant> updatedVariants = new ArrayList<>();
        for (ProductVariantRequest request : requests) {
            ProductVariant variant;
            BigDecimal existingPrice = null;
            BigDecimal existingCostPrice = null;
            boolean newVariant = request.getId() == null;
            if (!newVariant) {
                variant = existingById.get(request.getId());
                if (variant == null) {
                    throw new AppException(ErrorCode.INVALID_REQUEST);
                }
                existingPrice = variant.getPrice();
                existingCostPrice = variant.getCostPrice();
                if (!Objects.equals(variant.getSku(), request.getSku())
                        && orderItemRepository.existsByVariant_Product_Id(productId)) {
                    throw new AppException(ErrorCode.PRODUCT_HAS_ORDERS);
                }
            } else {
                variant = new ProductVariant();
                variant.setProduct(product);
                variant.setCreatedBy(product.getUpdatedBy());
            }

            productVariantMapper.updateEntityFromRequest(request, variant);
            if (variant.getPrice() == null) {
                variant.setPrice(newVariant ? BigDecimal.ZERO : existingPrice);
            }
            variant.setCostPrice(newVariant ? BigDecimal.ZERO : existingCostPrice);
            if (variant.getOptionValues() == null) {
                variant.setOptionValues(new HashMap<>());
            }
            if (variant.getIsActive() == null) {
                variant.setIsActive(true);
            }
            variant.setUpdatedBy(product.getUpdatedBy());
            updatedVariants.add(variant);
        }

        List<ProductVariant> savedVariants = productVariantRepository.saveAll(updatedVariants);
        markChangedVariantsOutOfSync(savedVariants, snapshots);
        List<ProductImage> variantImages = buildVariantImages(product, requests, savedVariants);
        productImageRepository.deleteByProductId(productId);
        productImageRepository.flush();
        if (!variantImages.isEmpty()) {
            productImageRepository.saveAll(variantImages);
        }
    }

    private void validateCreateBarcodes(List<ProductVariantRequest> requests) {
        List<String> barcodes = requests.stream()
                .map(ProductVariantRequest::getBarcode)
                .filter(barcode -> barcode != null && !barcode.trim().isEmpty())
                .toList();
        if (!barcodes.isEmpty()) {
            if (new HashSet<>(barcodes).size() < barcodes.size()) {
                throw new AppException(ErrorCode.VARIANT_BARCODE_CONFLICT);
            }
            if (productVariantRepository.existsByBarcodeInAndDeletedAtIsNull(barcodes)) {
                throw new AppException(ErrorCode.VARIANT_BARCODE_CONFLICT);
            }
        }
    }

    private void validateUpdate(UUID productId, List<ProductVariantRequest> requests) {
        List<String> skus = requests.stream().map(ProductVariantRequest::getSku).toList();
        if (!skus.isEmpty()) {
            if (new HashSet<>(skus).size() < skus.size()) {
                throw new AppException(ErrorCode.VARIANT_SKU_CONFLICT);
            }
            if (productVariantRepository.existsBySkuInAndProductIdNotAndDeletedAtIsNull(skus, productId)) {
                throw new AppException(ErrorCode.VARIANT_SKU_CONFLICT);
            }
            if (productRepository.existsBySkuInAndIdNotAndDeletedAtIsNull(skus, productId)) {
                throw new AppException(ErrorCode.PRODUCT_SKU_CONFLICT);
            }
        }
        List<String> barcodes = requests.stream()
                .map(ProductVariantRequest::getBarcode)
                .filter(barcode -> barcode != null && !barcode.trim().isEmpty())
                .toList();
        if (!barcodes.isEmpty()) {
            if (new HashSet<>(barcodes).size() < barcodes.size()) {
                throw new AppException(ErrorCode.VARIANT_BARCODE_CONFLICT);
            }
            if (productVariantRepository.existsByBarcodeInAndProductIdNotAndDeletedAtIsNull(barcodes, productId)) {
                throw new AppException(ErrorCode.VARIANT_BARCODE_CONFLICT);
            }
        }
    }

    private void deleteMissingVariants(UUID productId,
                                       List<ProductVariant> existingVariants,
                                       Set<UUID> incomingIds) {
        List<ProductVariant> variantsToDelete = existingVariants.stream()
                .filter(variant -> !incomingIds.contains(variant.getId()))
                .toList();
        if (variantsToDelete.isEmpty()) {
            return;
        }
        List<UUID> ids = variantsToDelete.stream().map(ProductVariant::getId).toList();
        List<UUID> idsWithOrders = orderItemRepository.findVariantIdsWithOrders(ids);
        for (ProductVariant variant : variantsToDelete) {
            if (idsWithOrders.contains(variant.getId())) {
                variant.setIsActive(false);
            } else {
                variant.setDeletedAt(OffsetDateTime.now());
            }
        }
        productVariantRepository.saveAll(variantsToDelete);
        productVariantRepository.flush();
    }

    private void validateExistingSkuIdentity(List<ProductVariant> existingVariants,
                                             List<ProductVariantRequest> requests) {
        for (ProductVariantRequest request : requests) {
            ProductVariant matching = existingVariants.stream()
                    .filter(existing -> existing.getSku().equals(request.getSku()))
                    .findFirst()
                    .orElse(null);
            if (matching != null && !matching.getId().equals(request.getId()) && matching.getDeletedAt() == null) {
                throw new AppException(ErrorCode.VARIANT_SKU_CONFLICT);
            }
        }
    }

    private void saveVariantImages(Product product,
                                   List<ProductVariantRequest> requests,
                                   List<ProductVariant> savedVariants) {
        List<ProductImage> images = buildVariantImages(product, requests, savedVariants);
        if (!images.isEmpty()) {
            productImageRepository.saveAll(images);
        }
    }

    private List<ProductImage> buildVariantImages(Product product,
                                                  List<ProductVariantRequest> requests,
                                                  List<ProductVariant> savedVariants) {
        List<ProductImage> images = new ArrayList<>();
        for (int index = 0; index < requests.size(); index++) {
            ProductVariantRequest request = requests.get(index);
            if (request.getImages() == null || request.getImages().isEmpty()) {
                continue;
            }
            long primaryCount = request.getImages().stream()
                    .filter(image -> Boolean.TRUE.equals(image.getIsPrimary()))
                    .count();
            if (primaryCount > 1) {
                throw new AppException(ErrorCode.INVALID_REQUEST);
            }
            ProductVariant savedVariant = savedVariants.get(index);
            images.addAll(request.getImages().stream()
                    .map(imageRequest -> {
                        ProductImage image = productImageMapper.toEntity(imageRequest);
                        image.setProduct(product);
                        image.setVariant(savedVariant);
                        return image;
                    })
                    .toList());
        }
        return images;
    }

    private void markChangedVariantsOutOfSync(List<ProductVariant> variants,
                                              Map<UUID, VariantSyncSnapshot> snapshots) {
        List<UUID> changedIds = variants.stream()
                .filter(variant -> variant.getId() != null)
                .filter(variant -> {
                    VariantSyncSnapshot existing = snapshots.get(variant.getId());
                    return existing == null
                            || !Objects.equals(existing.sku(), variant.getSku())
                            || !sameAmount(existing.price(), variant.getPrice());
                })
                .map(ProductVariant::getId)
                .toList();
        if (changedIds.isEmpty()) {
            return;
        }
        List<ChannelProductVariant> mappings =
                channelProductVariantRepository.findActiveByVariantIdInWithChannel(changedIds);
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
}
