package fu.osms.catalog.service.impl;

import fu.osms.catalog.dto.request.CategorySuggestionRequest;
import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductImage;
import fu.osms.catalog.repository.ProductImageRepository;
import fu.osms.catalog.repository.ProductRepository;
import fu.osms.catalog.service.CategorySuggestionInputService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CategorySuggestionInputServiceImpl implements CategorySuggestionInputService {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;

    @Override
    public CategorySuggestionRequest resolve(CategorySuggestionRequest request) {
        if (request == null || request.getProductId() == null) {
            return request;
        }

        Product product = productRepository.findByIdAndDeletedAtIsNull(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found for category suggestion"));
        ProductImage primaryImage = productImageRepository.findByProductIdOrderByIsPrimaryDescSortOrderAsc(product.getId())
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Product needs a primary image before category suggestion"));

        request.setTitle(product.getName());
        request.setDescription(product.getDescription());
        request.setPrimaryImageUrl(primaryImage.getUrl());
        return request;
    }
}
