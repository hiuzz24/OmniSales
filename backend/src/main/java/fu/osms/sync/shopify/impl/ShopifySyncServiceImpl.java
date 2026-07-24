package fu.osms.sync.shopify.impl;

import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductImage;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.util.ProductCostPolicy;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelCredential;
import fu.osms.channel.entity.ChannelProduct;
import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.channel.repository.ChannelProductRepository;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.common.enums.SyncStatus;
import fu.osms.sync.service.PlatformSyncService;
import fu.osms.sync.shopify.ShopifyApiClient;
import fu.osms.sync.shopify.ShopifyPayloadBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import fu.osms.sync.dto.shopify.request.ShopifyProductPayload;
import fu.osms.sync.dto.shopify.response.ShopifyProductResponse;
import fu.osms.sync.dto.shopify.response.ShopifyVariantResponse;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShopifySyncServiceImpl implements PlatformSyncService {

    private final ShopifyApiClient shopifyApiClient;
    private final ShopifyPayloadBuilder shopifyPayloadBuilder;
    private final ChannelCredentialRepository channelCredentialRepository;
    private final ChannelProductRepository channelProductRepository;
    private final ChannelProductVariantRepository channelProductVariantRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Override
    public boolean syncProduct(Product product,
                               List<ProductVariant> variants,
                               List<ProductImage> images,
                               Channel channel,
                               ChannelProduct channelProduct) {
        try {
            String shopDomain = extractShopDomain(channel);
            String accessToken = channelCredentialRepository
                    .findByChannelIdAndConnectionState(channel.getId(), "CONNECTED")
                    .map(ChannelCredential::getAccessToken)
                    .orElse(null);

            if (shopDomain == null || shopDomain.isBlank()) {
                throw new IllegalArgumentException("Shop domain is missing in channel metadata");
            }
            if (accessToken == null || accessToken.isBlank()) {
                throw new IllegalArgumentException("Access token is missing for channel: " + channel.getDisplayName());
            }

            ShopifyProductResponse shopifyResponse;
            boolean isNew = (channelProduct.getExternalProductId() == null);

            ShopifyProductPayload payload = shopifyPayloadBuilder.buildPayload(product, variants, images);
            if (!isNew) {
                // Shopify REST product update rejects option changes without matching variant payloads.
                // Existing variants are synced separately through productVariantsBulkUpdate below.
                payload.setVariants(null);
                payload.setOptions(null);
            }

            if (isNew) {
                shopifyResponse = shopifyApiClient.createProduct(shopDomain, accessToken, payload);
            } else {
                shopifyResponse = shopifyApiClient.updateProduct(
                        shopDomain, accessToken, channelProduct.getExternalProductId(), payload);
            }

            if (shopifyResponse == null) {
                throw new IllegalStateException("Shopify returned an empty product response");
            }

            try {
                log.info("[ShopifySync] Raw Shopify Response: {}", objectMapper.writeValueAsString(shopifyResponse));
            } catch (Exception ex) {
                log.warn("[ShopifySync] Could not serialize Shopify response to JSON", ex);
            }

            if (isNew) {
                channelProduct.setExternalProductId(String.valueOf(shopifyResponse.getId()));
            }

            if (shopifyResponse.getVariants() != null) {
                log.info("[ShopifySync] Response has {} variants", shopifyResponse.getVariants().size());
                int variantIndex = 0;
                Set<java.util.UUID> matchedLocalVariantIds = new HashSet<>();
                for (ShopifyVariantResponse shopifyVar : shopifyResponse.getVariants()) {
                        String sku = shopifyVar.getSku();
                        log.info("[ShopifySync] Variant from Shopify: id={}, sku='{}', inventoryItemId={}",
                                shopifyVar.getId(), sku, shopifyVar.getInventoryItemId());
                                
                        if (sku == null || sku.isBlank()) {
                            log.warn("[ShopifySync] SKU is empty, SKIPPING normal match");
                        }

                        Optional<ProductVariant> matchedVariantOpt = Optional.empty();
                        
                        if (sku != null && !sku.isBlank()) {
                            matchedVariantOpt = variants.stream()
                                    .filter(v -> sku.equals(v.getSku()))
                                    .filter(v -> v.getId() == null || !matchedLocalVariantIds.contains(v.getId()))
                                    .findFirst();
                        }
                        
                        if (matchedVariantOpt.isEmpty() && variantIndex < variants.size()
                                && (variants.get(variantIndex).getId() == null
                                || !matchedLocalVariantIds.contains(variants.get(variantIndex).getId()))) {
                            log.warn("[ShopifySync] SKU match failed or empty, using index fallback: index={}", variantIndex);
                            matchedVariantOpt = Optional.of(variants.get(variantIndex));
                        } else if (matchedVariantOpt.isEmpty()) {
                            matchedVariantOpt = variants.stream()
                                    .filter(v -> v.getId() == null || !matchedLocalVariantIds.contains(v.getId()))
                                    .findFirst();
                        }

                        if (matchedVariantOpt.isPresent()) {
                            ProductVariant localVariant = matchedVariantOpt.get();
                            if (localVariant.getId() != null) {
                                matchedLocalVariantIds.add(localVariant.getId());
                            }
                            String externalVariantId = String.valueOf(shopifyVar.getId());

                            ChannelProductVariant cpv = channelProductVariantRepository
                                    .findByChannelProductIdAndVariantId(channelProduct.getId(), localVariant.getId())
                                    .orElse(ChannelProductVariant.builder()
                                            .channelProduct(channelProduct)
                                            .variant(localVariant)
                                            .externalVariantId(externalVariantId)
                                            .build());

                            cpv.setExternalVariantId(externalVariantId);
                            cpv.setExternalSku(shopifyVar.getSku());
                            if (shopifyVar.getPrice() != null && !shopifyVar.getPrice().isBlank()) {
                                cpv.setExternalPrice(new java.math.BigDecimal(shopifyVar.getPrice()));
                            }
                            cpv.setSyncStatus(SyncStatus.SYNCED);
                            cpv.setLastSyncedAt(OffsetDateTime.now());

                            if (shopifyVar.getInventoryItemId() != null) {
                                Map<String, Object> metadata = cpv.getMetadata();
                                if (metadata == null) {
                                    metadata = new HashMap<>();
                                }
                                metadata.put("inventory_item_id", shopifyVar.getInventoryItemId());
                                cpv.setMetadata(metadata);
                            }

                            channelProductVariantRepository.save(cpv);
                            log.info("[ShopifySync] Saved ChannelProductVariant for local variant {} with externalId {}", localVariant.getId(), externalVariantId);
                        } else {
                            log.warn("[ShopifySync] Could not match Shopify variant to any local variant.");
                        }
                        variantIndex++;
                }
            } else {
                log.warn("[ShopifySync] Response variants is NULL — no variants to map!");
            }

            syncVariantPricesAndCosts(shopDomain, accessToken, channelProduct, variants);

            channelProduct.setSyncStatus(SyncStatus.SYNCED);
            channelProduct.setLastSyncedAt(OffsetDateTime.now());
            channelProduct.setLastSyncError(null);
            channelProductRepository.save(channelProduct);
            return true;
        } catch (Exception e) {
            log.error("Failed to sync product '{}' to Shopify: {}", product.getName(), e.getMessage(), e);
            channelProduct.setSyncStatus(SyncStatus.FAILED);
            channelProduct.setLastSyncError(e.getMessage());
            channelProductRepository.save(channelProduct);
            return false;
        }
    }

    private void attachExistingVariantIds(ShopifyProductPayload payload, ChannelProduct channelProduct) {
        if (payload.getVariants() == null || payload.getVariants().isEmpty()) {
            return;
        }

        Map<String, Long> externalVariantIdBySku = new HashMap<>();
        List<ChannelProductVariant> channelProductVariants =
                channelProductVariantRepository.findByChannelProductId(channelProduct.getId());

        for (ChannelProductVariant channelProductVariant : channelProductVariants) {
            ProductVariant variant = channelProductVariant.getVariant();
            if (variant != null && variant.getSku() != null && channelProductVariant.getExternalVariantId() != null) {
                externalVariantIdBySku.put(variant.getSku(), Long.valueOf(channelProductVariant.getExternalVariantId()));
            }
        }

        payload.getVariants().forEach(variantPayload ->
                variantPayload.setId(externalVariantIdBySku.get(variantPayload.getSku())));
    }

    @SuppressWarnings("unchecked")
    private void syncVariantPricesAndCosts(String shopDomain,
                                           String accessToken,
                                           ChannelProduct channelProduct,
                                           List<ProductVariant> variants) {
        List<ChannelProductVariant> channelProductVariants =
                channelProductVariantRepository.findByChannelProductId(channelProduct.getId());
        if (channelProductVariants.isEmpty()) {
            log.warn("[ShopifySync] Skip variant price/cost sync because no channel variant mapping exists channelProductId={}",
                    channelProduct.getId());
            return;
        }

        Map<java.util.UUID, ProductVariant> localVariantById = new HashMap<>();
        for (ProductVariant variant : variants) {
            if (variant.getId() != null && Boolean.TRUE.equals(variant.getIsActive()) && variant.getDeletedAt() == null) {
                localVariantById.put(variant.getId(), variant);
            }
        }

        List<Map<String, Object>> variantInputs = new ArrayList<>();
        for (ChannelProductVariant mapping : channelProductVariants) {
            ProductVariant localVariant = mapping.getVariant();
            if (localVariant == null || localVariant.getId() == null) {
                continue;
            }
            localVariant = localVariantById.getOrDefault(localVariant.getId(), localVariant);
            if (mapping.getExternalVariantId() == null || mapping.getExternalVariantId().isBlank()) {
                log.warn("[ShopifySync] Skip variant without externalVariantId localVariantId={}", localVariant.getId());
                continue;
            }

            BigDecimal price = localVariant.getPrice() != null ? localVariant.getPrice() : BigDecimal.ZERO;
            BigDecimal cost = ProductCostPolicy.initialCost(localVariant.getCostPrice(), price);

            Map<String, Object> inventoryItemInput = new HashMap<>();
            inventoryItemInput.put("cost", cost);
            inventoryItemInput.put("tracked", true);

            Map<String, Object> variantInput = new HashMap<>();
            variantInput.put("id", toProductVariantGid(mapping.getExternalVariantId()));
            variantInput.put("price", price.toPlainString());
            variantInput.put("inventoryItem", inventoryItemInput);
            variantInputs.add(variantInput);
        }

        if (variantInputs.isEmpty()) {
            return;
        }

        String mutation = """
                mutation productVariantsBulkUpdate($productId: ID!, $variants: [ProductVariantsBulkInput!]!) {
                  productVariantsBulkUpdate(productId: $productId, variants: $variants) {
                    productVariants {
                      id
                      price
                      inventoryItem {
                        id
                        unitCost {
                          amount
                        }
                        tracked
                      }
                    }
                    userErrors {
                      field
                      message
                    }
                  }
                }
                """;

        Map<String, Object> response = shopifyApiClient.executeGraphQl(
                shopDomain,
                accessToken,
                mutation,
                Map.of(
                        "productId", toProductGid(channelProduct.getExternalProductId()),
                        "variants", variantInputs
                )
        );
        ensureNoGraphQlErrors(response);

        Map<String, Object> data = (Map<String, Object>) response.get("data");
        Map<String, Object> payload = data == null ? null : (Map<String, Object>) data.get("productVariantsBulkUpdate");
        List<Map<String, Object>> userErrors = payload == null ? List.of() : (List<Map<String, Object>>) payload.get("userErrors");
        if (userErrors != null && !userErrors.isEmpty()) {
            throw new IllegalStateException("Shopify productVariantsBulkUpdate lỗi: " + userErrors);
        }

        Map<String, ChannelProductVariant> mappingByExternalVariantId = new HashMap<>();
        for (ChannelProductVariant mapping : channelProductVariants) {
            mappingByExternalVariantId.put(numericId(mapping.getExternalVariantId()), mapping);
        }

        List<Map<String, Object>> updatedVariants = payload == null
                ? List.of()
                : (List<Map<String, Object>>) payload.get("productVariants");
        OffsetDateTime syncedAt = OffsetDateTime.now();
        for (Map<String, Object> updatedVariant : updatedVariants) {
            ChannelProductVariant mapping = mappingByExternalVariantId.get(numericId(String.valueOf(updatedVariant.get("id"))));
            if (mapping == null) {
                continue;
            }
            Object price = updatedVariant.get("price");
            if (price != null) {
                mapping.setExternalPrice(new BigDecimal(price.toString()));
            }

            Map<String, Object> metadata = mapping.getMetadata() == null
                    ? new HashMap<>()
                    : new HashMap<>(mapping.getMetadata());
            Map<String, Object> inventoryItem = (Map<String, Object>) updatedVariant.get("inventoryItem");
            if (inventoryItem != null && inventoryItem.get("id") != null) {
                metadata.put("inventory_item_id", numericId(inventoryItem.get("id").toString()));
            }
            if (inventoryItem != null && inventoryItem.get("unitCost") instanceof Map<?, ?> unitCost
                    && unitCost.get("amount") != null) {
                metadata.put("shopifyUnitCost", unitCost.get("amount").toString());
            }
            mapping.setMetadata(metadata);
            mapping.setSyncStatus(SyncStatus.SYNCED);
            mapping.setLastSyncedAt(syncedAt);
            channelProductVariantRepository.save(mapping);
        }
    }

    private void ensureNoGraphQlErrors(Map<String, Object> response) {
        Object errors = response.get("errors");
        if (errors != null) {
            throw new IllegalStateException("Shopify GraphQL errors: " + errors);
        }
    }

    private String toProductGid(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Shopify product id is missing.");
        }
        if (value.startsWith("gid://shopify/Product/")) {
            return value;
        }
        return "gid://shopify/Product/" + numericId(value);
    }

    private String toProductVariantGid(String value) {
        if (value.startsWith("gid://shopify/ProductVariant/")) {
            return value;
        }
        return "gid://shopify/ProductVariant/" + numericId(value);
    }

    private String numericId(String value) {
        if (value == null) {
            return null;
        }
        int index = value.lastIndexOf('/');
        return index >= 0 ? value.substring(index + 1) : value;
    }

    private String extractShopDomain(Channel channel) {
        if (channel.getMetadata() == null) return null;
        Object shopDomain = channel.getMetadata().get("shopDomain");
        return shopDomain != null ? shopDomain.toString() : null;
    }
}
