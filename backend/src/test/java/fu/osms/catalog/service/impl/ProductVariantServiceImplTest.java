package fu.osms.catalog.service.impl;

import fu.osms.catalog.dto.response.ProductVariantResponse;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.mapper.ProductVariantMapper;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.common.dto.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductVariantServiceImpl Tests")
class ProductVariantServiceImplTest {

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private ProductVariantMapper productVariantMapper;

    @InjectMocks
    private ProductVariantServiceImpl productVariantService;

    private ProductVariant testVariant;
    private ProductVariantResponse testResponse;

    @BeforeEach
    void setUp() {
        testVariant = ProductVariant.builder()
                .id(UUID.randomUUID())
                .sku("SKU-001")
                .name("Test Variant")
                .price(java.math.BigDecimal.valueOf(100.00))
                .build();

        testResponse = ProductVariantResponse.builder()
                .id(testVariant.getId())
                .sku("SKU-001")
                .name("Test Variant")
                .price(java.math.BigDecimal.valueOf(100.00))
                .build();
    }

    @Nested
    @DisplayName("search() Tests")
    class SearchTests {

        @Test
        @DisplayName("Should call findAllActive when keyword is null")
        void search_withNullKeyword() {
            PageRequest pageable = PageRequest.of(0, 10);
            Page<ProductVariant> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

            when(productVariantRepository.findAllActive(pageable)).thenReturn(emptyPage);

            PageResponse<ProductVariantResponse> result = productVariantService.search(null, 0, 10);

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
            verify(productVariantRepository).findAllActive(pageable);
        }

        @Test
        @DisplayName("Should call findAllActive when keyword is blank")
        void search_withBlankKeyword() {
            PageRequest pageable = PageRequest.of(0, 10);
            Page<ProductVariant> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

            when(productVariantRepository.findAllActive(pageable)).thenReturn(emptyPage);

            PageResponse<ProductVariantResponse> result = productVariantService.search("   ", 0, 10);

            assertThat(result.getContent()).isEmpty();
            verify(productVariantRepository).findAllActive(pageable);
        }

        @Test
        @DisplayName("Should call searchByKeyword with trimmed keyword")
        void search_withValidKeyword() {
            PageRequest pageable = PageRequest.of(0, 10);
            List<ProductVariant> variants = List.of(testVariant);
            Page<ProductVariant> page = new PageImpl<>(variants, pageable, 1);

            when(productVariantRepository.searchByKeyword(eq("test keyword"), any(PageRequest.class))).thenReturn(page);
            when(productVariantMapper.toResponse(testVariant)).thenReturn(testResponse);

            PageResponse<ProductVariantResponse> result = productVariantService.search("  test keyword  ", 0, 10);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getSku()).isEqualTo("SKU-001");
            verify(productVariantRepository).searchByKeyword(eq("test keyword"), any(PageRequest.class));
        }

        @Test
        @DisplayName("Should return empty page when no results")
        void search_withEmptyResult() {
            PageRequest pageable = PageRequest.of(0, 10);
            Page<ProductVariant> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

            when(productVariantRepository.findAllActive(pageable)).thenReturn(emptyPage);

            PageResponse<ProductVariantResponse> result = productVariantService.search(null, 0, 10);

            assertThat(result.getTotalElements()).isZero();
            assertThat(result.getTotalPages()).isZero();
            assertThat(result.isFirst()).isTrue();
            assertThat(result.isLast()).isTrue();
        }

        @Test
        @DisplayName("Should return correct pagination metadata")
        void search_withPagination() {
            PageRequest pageable = PageRequest.of(0, 2);
            List<ProductVariant> variants = List.of(testVariant, testVariant);
            Page<ProductVariant> page = new PageImpl<>(variants, pageable, 5);

            when(productVariantRepository.findAllActive(pageable)).thenReturn(page);
            when(productVariantMapper.toResponse(any(ProductVariant.class))).thenReturn(testResponse);

            PageResponse<ProductVariantResponse> result = productVariantService.search(null, 0, 2);

            assertThat(result.getTotalElements()).isEqualTo(5);
            assertThat(result.getTotalPages()).isEqualTo(3);
            assertThat(result.isFirst()).isTrue();
            assertThat(result.isLast()).isFalse();
            assertThat(result.getSize()).isEqualTo(2);
            assertThat(result.getPage()).isZero();
        }

        @Test
        @DisplayName("Should handle last page correctly")
        void search_lastPage() {
            PageRequest pageable = PageRequest.of(2, 2);
            List<ProductVariant> variants = List.of(testVariant);
            Page<ProductVariant> page = new PageImpl<>(variants, pageable, 5);

            when(productVariantRepository.findAllActive(pageable)).thenReturn(page);
            when(productVariantMapper.toResponse(any(ProductVariant.class))).thenReturn(testResponse);

            PageResponse<ProductVariantResponse> result = productVariantService.search(null, 2, 2);

            assertThat(result.isFirst()).isFalse();
            assertThat(result.isLast()).isTrue();
        }
    }
}
