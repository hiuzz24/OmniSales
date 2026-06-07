package fu.osms.catalog.service.impl;

import fu.osms.catalog.dto.request.ProductRequest;
import fu.osms.catalog.dto.response.ProductResponse;
import fu.osms.catalog.entity.Category;
import fu.osms.catalog.entity.Product;
import fu.osms.catalog.enums.ProductStatus;
import fu.osms.catalog.mapper.ProductMapper;
import fu.osms.catalog.repository.CategoryRepository;
import fu.osms.catalog.repository.ProductRepository;
import fu.osms.catalog.service.ProductService;
import fu.osms.common.dto.PageResponse;
import fu.osms.shop.entity.Shop;
import fu.osms.shop.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ShopRepository shopRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;

    @Override
    @Transactional
    public ProductResponse create(ProductRequest request) {
        Shop shop = shopRepository.findById(request.getShopId())
                .orElseThrow(() -> new RuntimeException("Shop not found: " + request.getShopId()));
        Product product = productMapper.toEntity(request);
        product.setShop(shop);
        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("Category not found"));
            product.setCategory(category);
        }
        return productMapper.toResponse(productRepository.save(product));
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getById(UUID id) {
        return productRepository.findByIdAndDeletedAtIsNull(id)
                .map(productMapper::toResponseWithDetails)
                .orElseThrow(() -> new RuntimeException("Product not found: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> getByShopId(UUID shopId, int page, int size) {
        Page<Product> pageResult = productRepository.findByShopIdAndDeletedAtIsNull(shopId, PageRequest.of(page, size));
        return toPageResponse(pageResult, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(UUID shopId, String keyword, ProductStatus status, int page, int size) {
        Page<Product> pageResult;
        if (keyword != null && !keyword.isBlank()) {
            pageResult = productRepository.searchByShopIdAndKeyword(shopId, keyword, PageRequest.of(page, size));
        } else if (status != null) {
            pageResult = productRepository.findByShopIdAndStatusAndDeletedAtIsNull(shopId, status, PageRequest.of(page, size));
        } else {
            pageResult = productRepository.findByShopIdAndDeletedAtIsNull(shopId, PageRequest.of(page, size));
        }
        return toPageResponse(pageResult, page, size);
    }

    @Override
    @Transactional
    public ProductResponse update(UUID id, ProductRequest request) {
        Product product = productRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Product not found: " + id));
        productMapper.updateEntityFromRequest(request, product);
        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("Category not found"));
            product.setCategory(category);
        }
        return productMapper.toResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    public ProductResponse updateStatus(UUID id, ProductStatus status) {
        Product product = productRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Product not found: " + id));
        product.setStatus(status);
        return productMapper.toResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Product product = productRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Product not found: " + id));
        product.setDeletedAt(OffsetDateTime.now());
        productRepository.save(product);
    }

    private PageResponse<ProductResponse> toPageResponse(Page<Product> pageResult, int page, int size) {
        return PageResponse.<ProductResponse>builder()
                .content(pageResult.getContent().stream().map(productMapper::toResponse).toList())
                .page(page).size(size)
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .first(pageResult.isFirst()).last(pageResult.isLast())
                .build();
    }
}
