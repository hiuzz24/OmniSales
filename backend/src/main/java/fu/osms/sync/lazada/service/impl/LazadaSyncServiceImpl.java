package fu.osms.sync.lazada.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import fu.osms.common.exception.TokenExpiredException;
import fu.osms.sync.lazada.service.LazadaApiClient;
import fu.osms.sync.lazada.service.LazadaImageService;
import fu.osms.sync.lazada.service.LazadaPayloadBuilder;
import fu.osms.sync.service.PlatformSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class LazadaSyncServiceImpl implements PlatformSyncService {

    private final LazadaApiClient lazadaApiClient;
    private final LazadaImageService lazadaImageService;
    private final LazadaPayloadBuilder lazadaPayloadBuilder;
    private final ChannelCredentialRepository channelCredentialRepository;
    private final ChannelProductRepository channelProductRepository;
    private final ChannelProductVariantRepository channelProductVariantRepository;
    private final ObjectMapper objectMapper;

    @Override
    public boolean syncProduct(Product product, List<ProductVariant> variants, List<ProductImage> images, Channel channel, ChannelProduct channelProduct) {
        try {
            List<ChannelProductVariant> channelProductVariants = channelProductVariantRepository.findByChannelProductId(channelProduct.getId());
            Map<String,String> mapExternalSkuId = new HashMap<>();
            for(ChannelProductVariant channelProductVariant : channelProductVariants){
                ProductVariant variant = channelProductVariant.getVariant();
                if(variant != null && variant.getSku() != null && channelProductVariant.getExternalVariantId() != null){
                    mapExternalSkuId.put(variant.getSku(),channelProductVariant.getExternalVariantId());
                }
            }

            ChannelCredential credential = channelCredentialRepository
                    .findByChannelIdAndConnectionState(channel.getId(), "CONNECTED")
                    .orElse(null);

            if (credential == null || credential.getAccessToken() == null || credential.getAccessToken().isBlank()) {
                throw new IllegalStateException("Access token is missing or channel disconnected");
            }

            Long tokenExpiresAt = null;
            if (credential.getTokenExpiresAt() != null) {
                tokenExpiresAt = credential.getTokenExpiresAt().toEpochSecond();
            }

            List<String> migratedImageUrls = lazadaImageService.migrateImages(images, credential.getAccessToken(), tokenExpiresAt);

            String xmlPayload = lazadaPayloadBuilder.buildPayload(product, variants, migratedImageUrls);

            Map<String, String> params = new HashMap<>();
            params.put("payload", xmlPayload);

            boolean isNew = (channelProduct.getExternalProductId() == null);
            String apiPath = isNew ? "/product/create" : "/product/update";

            String responseStr = lazadaApiClient.executePost(apiPath, params, credential.getAccessToken(), tokenExpiresAt);
            log.error("[LazadaSync] Raw Lazada response for {}: {}", apiPath, responseStr);
            JsonNode root = objectMapper.readTree(responseStr);

            if (root.has("code") && "0".equals(root.get("code").asText())) {
                JsonNode data = root.path("data");
                if (data != null && data.has("item_id")) {
                    String itemId = data.get("item_id").asText();
                    if (isNew) {
                        channelProduct.setExternalProductId(itemId);
                    }

                    JsonNode skuList = data.path("sku_list");
                    if (skuList != null && skuList.isArray()) {
                        int variantIndex = 0;
                        for (JsonNode skuNode : skuList) {
                            String sellerSku = skuNode.path("seller_sku").asText(null);
                            String skuId = skuNode.path("sku_id").asText(null);

                            if (skuId != null) {
                                Optional<ProductVariant> matchedVariantOpt = Optional.empty();

                                if (sellerSku != null && !sellerSku.isBlank()) {
                                    matchedVariantOpt = variants.stream()
                                            .filter(v -> sellerSku.equals(v.getSku()))
                                            .findFirst();
                                }

                                if (matchedVariantOpt.isEmpty() && variantIndex < variants.size()) {
                                    log.warn("[LazadaSync] SKU match failed or empty, using index fallback: index={}", variantIndex);
                                    matchedVariantOpt = Optional.of(variants.get(variantIndex));
                                }

                                if (matchedVariantOpt.isPresent()) {
                                    ProductVariant localVariant = matchedVariantOpt.get();

                                    ChannelProductVariant cpv = channelProductVariantRepository
                                            .findByChannelProductIdAndVariantId(channelProduct.getId(), localVariant.getId())
                                            .orElse(ChannelProductVariant.builder()
                                                    .channelProduct(channelProduct)
                                                    .variant(localVariant)
                                                    .externalVariantId(skuId)
                                                    .build());

                                    cpv.setExternalVariantId(skuId);
                                    cpv.setExternalSku(sellerSku);
                                    cpv.setSyncStatus(SyncStatus.SYNCED);
                                    cpv.setLastSyncedAt(OffsetDateTime.now());

                                    channelProductVariantRepository.save(cpv);
                                }
                            }
                            variantIndex++;
                        }
                    }

                    channelProduct.setSyncStatus(SyncStatus.SYNCED);
                    channelProduct.setLastSyncedAt(OffsetDateTime.now());
                    channelProduct.setLastSyncError(null);
                    channelProductRepository.save(channelProduct);
                    return true;
                } else {
                    throw new RuntimeException("Missing item_id in response data");
                }
            } else {
                log.error("[LazadaSync] Lazada create/update failed. apiPath={}, payload={}, response={}",
                        apiPath, xmlPayload, responseStr);
                String errorMsg = root.has("message") ? root.get("message").asText() : "Unknown API error";
                throw new RuntimeException("Lazada API returned error: " + errorMsg);
            }

        } catch (TokenExpiredException e) {
            log.error("[LazadaSync] Token expired for channel {}", channel.getId(), e);
            throw e;
        } catch (Exception e) {
            log.error("[LazadaSync] Failed to sync product '{}' to Lazada: {}", product.getName(), e.getMessage(), e);
            channelProduct.setSyncStatus(SyncStatus.FAILED);
            channelProduct.setLastSyncError(e.getMessage());
            channelProductRepository.save(channelProduct);
            return false;
        }
    }
}
