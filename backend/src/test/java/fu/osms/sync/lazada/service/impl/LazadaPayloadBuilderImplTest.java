package fu.osms.sync.lazada.service.impl;

import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductVariant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LazadaPayloadBuilderImplTest {

    private LazadaPayloadBuilderImpl builder;

    @BeforeEach
    void setUp() {
        builder = new LazadaPayloadBuilderImpl();
        ReflectionTestUtils.setField(builder, "defaultCategoryId", "7932");
        ReflectionTestUtils.setField(builder, "defaultPackageWeight", "0.5");
        ReflectionTestUtils.setField(builder, "defaultPackageLength", "20");
        ReflectionTestUtils.setField(builder, "defaultPackageWidth", "15");
        ReflectionTestUtils.setField(builder, "defaultPackageHeight", "5");
    }

    private Product product(String name) {
        return Product.builder()
                .id(java.util.UUID.randomUUID())
                .name(name)
                .brand("BrandX")
                .description("Desc")
                .attributes(Map.of())
                .build();
    }

    private ProductVariant variant(String sku, String name, BigDecimal price) {
        return ProductVariant.builder()
                .id(java.util.UUID.randomUUID())
                .sku(sku)
                .name(name)
                .price(price)
                .weightGrams(500)
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("buildPayload — Create mode emits <PrimaryCategory>")
    void buildPayload_createMode_hasPrimaryCategory() {
        Product product = product("Áo thun");
        ProductVariant v = variant("SKU-1", "Red", new BigDecimal("100000"));

        String xml = builder.buildPayload(product, List.of(v), List.of("https://laz/img1.jpg"), Map.of(), true);

        assertThat(xml).contains("<PrimaryCategory>7932</PrimaryCategory>");
        assertThat(xml).contains("<name><![CDATA[Áo thun]]></name>");
        assertThat(xml).contains("<brand><![CDATA[BrandX]]></brand>");
        assertThat(xml).contains("<description><![CDATA[Desc]]></description>");
        assertThat(xml).contains("<SellerSku>SKU-1</SellerSku>");
        assertThat(xml).contains("<price>100000</price>");
        assertThat(xml).contains("<Image>https://laz/img1.jpg</Image>");
        assertThat(xml).contains("<package_weight>0.5</package_weight>");
    }

    @Test
    @DisplayName("buildPayload — Update mode omits <PrimaryCategory>")
    void buildPayload_updateMode_skipsPrimaryCategory() {
        Product product = product("Áo thun");
        ProductVariant v = variant("SKU-1", null, BigDecimal.ZERO);

        String xml = builder.buildPayload(product, List.of(v), List.of(), Map.of(), false);

        assertThat(xml).doesNotContain("<PrimaryCategory>");
        assertThat(xml).contains("<SellerSku>SKU-1</SellerSku>");
    }

    @Test
    @DisplayName("buildPayload — include SkuId when SKU mapping provided")
    void buildPayload_mapsSkuId() {
        Product product = product("P");
        ProductVariant v = variant("SKU-1", null, BigDecimal.ZERO);

        String xml = builder.buildPayload(product, List.of(v), List.of(), Map.of("SKU-1", "LZS-123"), true);

        assertThat(xml).contains("<SkuId>LZS-123</SkuId>");
        assertThat(xml).contains("<SellerSku>SKU-1</SellerSku>");
    }

    @Test
    @DisplayName("buildPayload — variant weightGrams overrides default")
    void buildPayload_variantWeightUsed() {
        Product product = product("P");
        ProductVariant v = variant("SKU-1", null, BigDecimal.ZERO);
        v.setWeightGrams(1500);

        String xml = builder.buildPayload(product, List.of(v), List.of(), Map.of(), true);

        assertThat(xml).contains("<package_weight>1.5</package_weight>");
    }

    @Test
    @DisplayName("buildPayload — empty/null image list omits <Images>")
    void buildPayload_noImages() {
        Product product = product("P");
        ProductVariant v = variant("SKU-1", null, BigDecimal.ZERO);

        String xml = builder.buildPayload(product, List.of(v), null, Map.of(), true);

        assertThat(xml).doesNotContain("<Images>");
    }

    @Test
    @DisplayName("buildPayload — cost price used when set")
    void buildPayload_costPriceUsed() {
        Product product = product("P");
        ProductVariant v = variant("SKU-1", null, new BigDecimal("100"));
        v.setCostPrice(new BigDecimal("75.5"));

        String xml = builder.buildPayload(product, List.of(v), List.of(), Map.of(), true);

        assertThat(xml).contains("<price>75.5</price>");
    }

    @Test
    @DisplayName("buildPayload — null product name still produces empty CDATA safely")
    void buildPayload_nullName_safe() {
        Product product = Product.builder()
                .id(java.util.UUID.randomUUID())
                .name(null)
                .brand(null)
                .build();
        ProductVariant v = variant("SKU-1", null, BigDecimal.ZERO);

        String xml = builder.buildPayload(product, List.of(v), List.of(), Map.of(), true);

        // The XML uses self-closing <name/> for null names, which means the
        // element is present but empty.
        assertThat(xml).containsPattern("<name\\s*/>");
        assertThat(xml).contains("<brand><![CDATA[No Brand]]></brand>");
    }
}
