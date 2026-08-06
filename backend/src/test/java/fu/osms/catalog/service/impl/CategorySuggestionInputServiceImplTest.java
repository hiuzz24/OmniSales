package fu.osms.catalog.service.impl;

import fu.osms.catalog.dto.request.CategorySuggestionRequest;
import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductImage;
import fu.osms.catalog.repository.ProductImageRepository;
import fu.osms.catalog.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategorySuggestionInputServiceImpl Tests")
class CategorySuggestionInputServiceImplTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductImageRepository productImageRepository;

    private CategorySuggestionInputServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CategorySuggestionInputServiceImpl(productRepository, productImageRepository);
    }

    @Test
    @DisplayName("resolve returns the request unchanged when request is null or productId is null")
    void resolve_returnsUnchangedWhenNoProductId() {
        assertThat(service.resolve(null)).isNull();

        CategorySuggestionRequest req = new CategorySuggestionRequest();
        req.setTitle("kept");
        CategorySuggestionRequest out = service.resolve(req);
        assertThat(out).isSameAs(req);
        assertThat(out.getTitle()).isEqualTo("kept");
    }

    @Test
    @DisplayName("resolve fills in title/description/primaryImageUrl from the product + its primary image")
    void resolve_copiesFieldsFromProduct() {
        UUID productId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();

        Product product = Product.builder()
                .id(productId)
                .name("Espresso Beans 1kg")
                .description("Premium dark roast")
                .build();
        when(productRepository.findByIdAndDeletedAtIsNull(productId)).thenReturn(Optional.of(product));

        ProductImage primary = ProductImage.builder()
                .url("https://cdn.osms.local/img/primary.jpg")
                .isPrimary(true)
                .build();
        when(productImageRepository.findByProductIdOrderByIsPrimaryDescSortOrderAsc(productId))
                .thenReturn(List.of(primary));

        CategorySuggestionRequest req = new CategorySuggestionRequest();
        req.setChannelId(channelId);
        req.setProductId(productId);

        CategorySuggestionRequest out = service.resolve(req);

        assertThat(out.getTitle()).isEqualTo("Espresso Beans 1kg");
        assertThat(out.getDescription()).isEqualTo("Premium dark roast");
        assertThat(out.getPrimaryImageUrl()).isEqualTo("https://cdn.osms.local/img/primary.jpg");
        assertThat(out.getChannelId()).isEqualTo(channelId); // passthrough
    }

    @Test
    @DisplayName("resolve throws IllegalArgumentException if product is not found")
    void resolve_throwsForUnknownProduct() {
        UUID productId = UUID.randomUUID();
        when(productRepository.findByIdAndDeletedAtIsNull(productId)).thenReturn(Optional.empty());

        CategorySuggestionRequest req = new CategorySuggestionRequest();
        req.setProductId(productId);

        assertThatThrownBy(() -> service.resolve(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Product not found");
    }

    @Test
    @DisplayName("resolve throws IllegalStateException if the product has no primary image")
    void resolve_throwsWhenNoPrimaryImage() {
        UUID productId = UUID.randomUUID();
        Product product = Product.builder().id(productId).name("Sample").description("d").build();
        when(productRepository.findByIdAndDeletedAtIsNull(productId)).thenReturn(Optional.of(product));
        when(productImageRepository.findByProductIdOrderByIsPrimaryDescSortOrderAsc(productId))
                .thenReturn(List.of());

        CategorySuggestionRequest req = new CategorySuggestionRequest();
        req.setProductId(productId);

        assertThatThrownBy(() -> service.resolve(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("primary image");
    }

    @Test
    @DisplayName("resolve picks only the first returned image as the primary candidate")
    void resolve_picksFirstImage() {
        UUID productId = UUID.randomUUID();
        Product product = Product.builder().id(productId).name("P").description("d").build();
        when(productRepository.findByIdAndDeletedAtIsNull(productId)).thenReturn(Optional.of(product));

        ProductImage first = ProductImage.builder().url("https://cdn/first.jpg").isPrimary(false).build();
        ProductImage second = ProductImage.builder().url("https://cdn/second.jpg").isPrimary(true).build();
        when(productImageRepository.findByProductIdOrderByIsPrimaryDescSortOrderAsc(productId))
                .thenReturn(List.of(first, second));

        CategorySuggestionRequest req = new CategorySuggestionRequest();
        req.setProductId(productId);

        CategorySuggestionRequest out = service.resolve(req);

        assertThat(out.getPrimaryImageUrl()).isEqualTo("https://cdn/first.jpg");
    }
}
