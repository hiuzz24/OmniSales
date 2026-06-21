package fu.osms.sync.service;

import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductImage;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelCredential;
import fu.osms.channel.entity.ChannelProduct;
import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.channel.repository.ChannelProductRepository;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.common.enums.SyncStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import fu.osms.sync.dto.shopify.request.ShopifyProductPayload;
import fu.osms.sync.dto.shopify.response.ShopifyProductResponse;
import fu.osms.sync.dto.shopify.response.ShopifyVariantResponse;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShopifySyncService implements PlatformSyncService {

    private final ShopifyApiClient shopifyApiClient;
    private final ShopifyPayloadBuilder shopifyPayloadBuilder;
    private final ChannelCredentialRepository channelCredentialRepository;
    private final ChannelProductRepository channelProductRepository;
    private final ChannelProductVariantRepository channelProductVariantRepository;

    @Override
    public boolean syncProduct(Product product,
                               List<ProductVariant> variants,
                               List<ProductImage> images,
                               Channel channel,
                               ChannelProduct channelProduct) {
        try {
            String shopDomain = extractShopDomain(channel);
            ChannelCredential credential = channelCredentialRepository.findByChannelId(channel.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Credential is missing for channel: " + channel.getDisplayName()));
            String accessToken = credential.getAccessToken();

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
                attachExistingVariantIds(payload, channelProduct);
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

            if (isNew) {
                channelProduct.setExternalProductId(String.valueOf(shopifyResponse.getId()));
            }

            if (shopifyResponse.getVariants() != null) {
                for (ShopifyVariantResponse shopifyVar : shopifyResponse.getVariants()) {
                        String sku = shopifyVar.getSku();
                        if (sku == null || sku.isBlank()) continue;

                        Optional<ProductVariant> matchedVariantOpt = variants.stream()
                                .filter(v -> sku.equals(v.getSku()))
                                .findFirst();

                        if (matchedVariantOpt.isPresent()) {
                            ProductVariant localVariant = matchedVariantOpt.get();
                            String externalVariantId = String.valueOf(shopifyVar.getId());

                            ChannelProductVariant cpv = channelProductVariantRepository
                                    .findByChannelProductIdAndVariantId(channelProduct.getId(), localVariant.getId())
                                    .orElse(ChannelProductVariant.builder()
                                            .channelProduct(channelProduct)
                                            .variant(localVariant)
                                            .externalVariantId(externalVariantId)
                                            .build());

                            cpv.setExternalVariantId(externalVariantId);
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
                        }
                }
            }

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

    private String extractShopDomain(Channel channel) {
        if (channel.getMetadata() == null) return null;
        Object shopDomain = channel.getMetadata().get("shopDomain");
        return shopDomain != null ? shopDomain.toString() : null;
    }
}
