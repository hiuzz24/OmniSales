package fu.osms.sync.shopify.impl;

import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductImage;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.enums.ProductStatus;
import fu.osms.sync.dto.shopify.request.ShopifyImagePayload;
import fu.osms.sync.dto.shopify.request.ShopifyProductPayload;
import fu.osms.sync.dto.shopify.request.ShopifyVariantPayload;
import fu.osms.sync.dto.shopify.request.ShopifyOptionPayload;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import fu.osms.sync.shopify.ShopifyPayloadBuilder;

@Service
public class ShopifyPayloadBuilderImpl implements ShopifyPayloadBuilder {

    public ShopifyProductPayload buildPayload(Product product, List<ProductVariant> variants, List<ProductImage> images) {
        String tags = null;
        if (product.getAttributes() != null && product.getAttributes().containsKey("tags")) {
            Object tagsObj = product.getAttributes().get("tags");
            if (tagsObj instanceof List) {
                tags = String.join(",", (List<String>) tagsObj);
            } else {
                tags = tagsObj.toString();
            }
        }

        List<ShopifyVariantPayload> variantPayloads = new ArrayList<>();
        for (ProductVariant v : variants) {
            if (!Boolean.TRUE.equals(v.getIsActive()) || v.getDeletedAt() != null) continue;

            String option1 = null;
            String option2 = null;
            String option3 = null;

            if (v.getOptionValues() != null && !v.getOptionValues().isEmpty()) {
                List<Object> values = v.getOptionValues().values().stream().toList();
                if (values.size() > 0) option1 = values.get(0).toString();
                if (values.size() > 1) option2 = values.get(1).toString();
                if (values.size() > 2) option3 = values.get(2).toString();
            }

            variantPayloads.add(ShopifyVariantPayload.builder()
                    .sku(v.getSku() != null ? v.getSku() : "")
                    .price(v.getPrice() != null ? v.getPrice().toPlainString() : "0.00")
                    .barcode(v.getBarcode())
                    .grams(v.getWeightGrams() != null ? v.getWeightGrams() : 0)
                    .option1(option1)
                    .option2(option2)
                    .option3(option3)
                    .build());
        }

        List<ShopifyImagePayload> imagePayloads = images.stream().map(img ->
                ShopifyImagePayload.builder()
                        .src(img.getUrl())
                        .position(img.getSortOrder() != null ? img.getSortOrder() + 1 : 1)
                        .build()
        ).collect(Collectors.toList());

        String status = (product.getStatus() == ProductStatus.ACTIVE) ? "active" : "draft";

        List<ShopifyOptionPayload> optionPayloads = new ArrayList<>();
        for (ProductVariant v : variants) {
            if (Boolean.TRUE.equals(v.getIsActive()) && v.getDeletedAt() == null) {
                if (v.getOptionValues() != null && !v.getOptionValues().isEmpty()) {
                    List<String> optionNames = v.getOptionValues().keySet().stream().toList();
                    if (optionNames.size() > 0) optionPayloads.add(ShopifyOptionPayload.builder().name(optionNames.get(0)).build());
                    if (optionNames.size() > 1) optionPayloads.add(ShopifyOptionPayload.builder().name(optionNames.get(1)).build());
                    if (optionNames.size() > 2) optionPayloads.add(ShopifyOptionPayload.builder().name(optionNames.get(2)).build());
                    break;
                }
            }
        }

        return ShopifyProductPayload.builder()
                .title(product.getName() != null ? product.getName() : "")
                .bodyHtml(product.getDescription() != null ? product.getDescription() : "")
                .vendor(product.getBrand() != null ? product.getBrand() : "")
                .tags(tags)
                .status(status)
                .variants(variantPayloads)
                .options(optionPayloads.isEmpty() ? null : optionPayloads)
                .images(imagePayloads)
                .build();
    }
}
