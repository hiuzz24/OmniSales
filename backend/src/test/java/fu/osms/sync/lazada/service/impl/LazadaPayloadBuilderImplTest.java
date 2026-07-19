package fu.osms.sync.lazada.service.impl;

import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.sync.lazada.dto.LazadaProductConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LazadaPayloadBuilderImplTest {

    private LazadaPayloadBuilderImpl builder;

    private Product product;
    private ProductVariant variant;

    @BeforeEach
    void setUp() {
        builder = new LazadaPayloadBuilderImpl();
        product = Product.builder()
                .id(java.util.UUID.randomUUID())
                .name("Test Product")
                .description("A great product")
                .brand("TestBrand")
                .unit("pcs")
                .weightGrams(800)
                .attributes(new HashMap<>())
                .build();
        Map<String, Object> attrs = product.getAttributes();
        attrs.put("packageLengthCm", "20");
        attrs.put("packageWidthCm", "15");
        attrs.put("packageHeightCm", "10");

        variant = ProductVariant.builder()
                .id(java.util.UUID.randomUUID())
                .product(product)
                .sku("SKU-001")
                .name("Default")
                .price(new BigDecimal("199.99"))
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("buildPayload — isCreate=true emits <PrimaryCategory> and <brand_id>")
    void buildPayload_create_containsCategoryAndBrand() {
        LazadaProductConfig config = LazadaProductConfig.builder()
                .categoryId("100001")
                .categoryName("Apparel")
                .brandId("9999")
                .brandName("TestBrand")
                .variantAttributeBindings(new HashMap<>())
                .variantAttributeValueMappings(new HashMap<>(Map.of(
                        "color_family", Map.of("SKU-001", "Red")
                )))
                .build();

        String xml = builder.buildPayload(product, List.of(variant),
                List.of("https://laz-img/1"), Map.of(), config, true);

        assertThat(xml).contains("<PrimaryCategory>100001</PrimaryCategory>");
        assertThat(xml).contains("<brand_id>9999</brand_id>");
        assertThat(xml).contains("<name><![CDATA[Test Product]]></name>");
        assertThat(xml).contains("<SellerSku>SKU-001</SellerSku>");
        assertThat(xml).contains("<color_family>Red</color_family>");
        assertThat(xml).contains("<Image>https://laz-img/1</Image>");
    }

    @Test
    @DisplayName("buildPayload — isCreate=false omits <PrimaryCategory> but keeps brand_id")
    void buildPayload_update_dropsPrimaryCategory() {
        LazadaProductConfig config = LazadaProductConfig.builder()
                .categoryId("100001")
                .categoryName("Apparel")
                .brandId("9999")
                .brandName("TestBrand")
                .variantAttributeBindings(new HashMap<>())
                .variantAttributeValueMappings(new HashMap<>(Map.of(
                        "color_family", Map.of("SKU-001", "Red")
                )))
                .build();

        String xml = builder.buildPayload(product, List.of(variant),
                List.of(), Map.of(), config, false);

        assertThat(xml).doesNotContain("<PrimaryCategory>");
        assertThat(xml).contains("<brand_id>9999</brand_id>");
    }

    @Test
    @DisplayName("buildPayload — externalSkuIdBySku emits <SkuId> for present mappings")
    void buildPayload_emitsExternalSkuId() {
        LazadaProductConfig config = LazadaProductConfig.builder()
                .categoryId("100001")
                .categoryName("Apparel")
                .brandId("9999")
                .brandName("TestBrand")
                .variantAttributeBindings(new HashMap<>())
                .variantAttributeValueMappings(new HashMap<>(Map.of(
                        "color_family", Map.of("SKU-001", "Red")
                )))
                .build();

        String xml = builder.buildPayload(product, List.of(variant),
                List.of(), Map.of("SKU-001", "EXT-SKU-ID-1"), config, true);

        assertThat(xml).contains("<SkuId>EXT-SKU-ID-1</SkuId>");
    }

    @Test
    @DisplayName("buildPayload — without external mapping, no <SkuId> element")
    void buildPayload_noExternalSkuId_omitsElement() {
        LazadaProductConfig config = LazadaProductConfig.builder()
                .categoryId("100001")
                .categoryName("Apparel")
                .brandId("9999")
                .brandName("TestBrand")
                .variantAttributeBindings(new HashMap<>())
                .variantAttributeValueMappings(new HashMap<>(Map.of(
                        "color_family", Map.of("SKU-001", "Red")
                )))
                .build();

        String xml = builder.buildPayload(product, List.of(variant),
                List.of(), Map.of(), config, true);

        assertThat(xml).doesNotContain("<SkuId>");
    }

    @Test
    @DisplayName("buildPayload — variant missing Lazada platform attribute value throws IllegalStateException")
    void buildPayload_missingVariantAttribute_throws() {
        LazadaProductConfig config = LazadaProductConfig.builder()
                .categoryId("100001")
                .categoryName("Apparel")
                .brandId("9999")
                .variantAttributeBindings(new HashMap<>())
                .variantAttributeValueMappings(new HashMap<>(Map.of(
                        "color_family", Map.of() // no value for SKU-001
                )))
                .build();

        assertThatThrownBy(() ->
                builder.buildPayload(product, List.of(variant), List.of(), Map.of(), config, true))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Missing Lazada color_family");
    }

    @Test
    @DisplayName("buildPayload — empty image list omits product Images element but still renders Skus")
    void buildPayload_emptyImages() {
        LazadaProductConfig config = LazadaProductConfig.builder()
                .categoryId("100001")
                .categoryName("Apparel")
                .brandId("9999")
                .variantAttributeBindings(new HashMap<>())
                .variantAttributeValueMappings(new HashMap<>(Map.of(
                        "color_family", Map.of("SKU-001", "Red")
                )))
                .build();

        String xml = builder.buildPayload(product, List.of(variant),
                List.of(), Map.of(), config, true);

        assertThat(xml).doesNotContain("<Images>");
        assertThat(xml).contains("<Skus>");
    }

    @Test
    @DisplayName("buildPayload — variant with price <= 0 throws IllegalStateException")
    void buildPayload_missingPrice_throws() {
        variant.setPrice(BigDecimal.ZERO);
        LazadaProductConfig config = LazadaProductConfig.builder()
                .categoryId("100001")
                .categoryName("Apparel")
                .brandId("9999")
                .variantAttributeBindings(new HashMap<>())
                .variantAttributeValueMappings(new HashMap<>())
                .build();

        assertThatThrownBy(() ->
                builder.buildPayload(product, List.of(variant), List.of(), Map.of(), config, true))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Missing selling price");
    }
}
