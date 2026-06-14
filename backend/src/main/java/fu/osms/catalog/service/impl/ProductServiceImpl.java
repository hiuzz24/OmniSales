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
import fu.osms.catalog.entity.ProductImage;
import fu.osms.catalog.dto.response.ProductImageResponse;
import fu.osms.catalog.dto.response.ProductVariantResponse;
import fu.osms.inventory.dto.response.StockSummaryDTO;
import fu.osms.inventory.service.InventoryService;
import fu.osms.catalog.service.ProductService;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelProduct;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.channel.repository.ChannelProductRepository;
import fu.osms.channel.service.ChannelService;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

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

    @Override
    @Transactional
    public ProductResponse create(ProductRequest request) {
        if(productRepository.existsBySkuAndDeletedAtIsNull(request.getSku())){
            throw new AppException(ErrorCode.PRODUCT_SKU_CONFLICT);
        } else if (productRepository.existsByNameAndDeletedAtIsNull(request.getName())) {
            throw new AppException(ErrorCode.PRODUCT_NAME_CONFLICT);
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

        return this.getById(savedProduct.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getById(UUID id) {
        Product product = productRepository.findById(id)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        ProductResponse response = productMapper.toResponse(product);

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
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    @Transactional
    public ProductResponse updateStatus(UUID id, ProductStatus status) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        throw new UnsupportedOperationException("Chưa code");
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
}
