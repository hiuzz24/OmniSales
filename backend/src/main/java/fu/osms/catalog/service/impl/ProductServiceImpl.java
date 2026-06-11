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
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;

    @Override
    @Transactional
    public ProductResponse create(ProductRequest request) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getById(UUID id) {
        throw new UnsupportedOperationException("Chưa code");
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
