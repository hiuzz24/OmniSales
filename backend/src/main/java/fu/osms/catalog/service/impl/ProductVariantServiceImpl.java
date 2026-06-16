package fu.osms.catalog.service.impl;

import fu.osms.catalog.dto.response.ProductVariantResponse;
import fu.osms.catalog.mapper.ProductVariantMapper;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.catalog.service.ProductVariantService;
import fu.osms.common.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductVariantServiceImpl implements ProductVariantService {

    private final ProductVariantRepository productVariantRepository;
    private final ProductVariantMapper productVariantMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductVariantResponse> search(String keyword, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);

        // Use different query path to avoid lower(bytea) error when keyword is null/blank
        Page<fu.osms.catalog.entity.ProductVariant> resultPage;
        if (keyword == null || keyword.isBlank()) {
            resultPage = productVariantRepository.findAllActive(pageable);
        } else {
            resultPage = productVariantRepository.searchByKeyword(keyword.trim(), pageable);
        }

        List<ProductVariantResponse> content = resultPage.getContent()
                .stream()
                .map(productVariantMapper::toResponse)
                .toList();

        return PageResponse.<ProductVariantResponse>builder()
                .content(content)
                .page(resultPage.getNumber())
                .size(resultPage.getSize())
                .totalElements(resultPage.getTotalElements())
                .totalPages(resultPage.getTotalPages())
                .first(resultPage.isFirst())
                .last(resultPage.isLast())
                .build();
    }
}
