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
import fu.osms.catalog.service.ProductService;
import fu.osms.common.dto.PageResponse;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.common.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

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
        
        List<ProductVariantResponse> variantResponses = variants.stream().map(v -> {
            ProductVariantResponse vr = productVariantMapper.toResponse(v);
            List<ProductImageResponse> vImgResponses = allImages.stream()
                    .filter(img -> img.getVariant() != null && img.getVariant().getId().equals(v.getId()))
                    .map(productImageMapper::toResponse)
                    .toList();
            vr.setImages(vImgResponses);
            return vr;
        }).toList();

        response.setVariants(variantResponses);

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> getAll(int page, int size) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(String keyword, ProductStatus status, int page, int size) {
        throw new UnsupportedOperationException("Chưa code");
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
        throw new UnsupportedOperationException("Chưa code");
    }
}
