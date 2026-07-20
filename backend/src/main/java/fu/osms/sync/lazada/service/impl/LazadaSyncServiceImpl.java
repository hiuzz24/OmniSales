package fu.osms.sync.lazada.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductImage;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelProduct;
import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.token.exception.PlatformAccessTokenExpiredException;
import fu.osms.channel.repository.ChannelProductRepository;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.common.enums.SyncStatus;
import fu.osms.sync.lazada.service.LazadaAuthorizedApiClient;
import fu.osms.sync.lazada.service.LazadaImageService;
import fu.osms.sync.lazada.service.LazadaPayloadBuilder;
import fu.osms.sync.lazada.dto.LazadaProductConfig;
import fu.osms.catalog.service.ProductChannelConfigService;
import fu.osms.sync.service.PlatformSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LazadaSyncServiceImpl implements PlatformSyncService {

    private final LazadaAuthorizedApiClient lazadaApiClient;
    private final LazadaImageService lazadaImageService;
    private final LazadaPayloadBuilder lazadaPayloadBuilder;
    private final ChannelProductRepository channelProductRepository;
    private final ChannelProductVariantRepository channelProductVariantRepository;
    private final ObjectMapper objectMapper;
    private final ProductChannelConfigService productChannelConfigService;

    @Override
    public boolean syncProduct(Product product, List<ProductVariant> variants, List<ProductImage> images, Channel channel, ChannelProduct channelProduct) {
        try {
            if (!productChannelConfigService.isReady(channelProduct)) {
                String error = productChannelConfigService.configurationError(channelProduct);
                throw new IllegalStateException(error == null ? "Missing Lazada product configuration" : error);
            }

            LazadaProductConfig config = resolveProductConfig(channelProduct);
            validateSyncPrerequisites(product, variants, config);
            List<String> migratedImageUrls = lazadaImageService.migrateImages(images, channel.getId());
            migrateSizeChartImage(config, channel.getId());

            boolean isNew = (channelProduct.getExternalProductId() == null);
            Map<String, String> externalSkuIdBySku = isNew ? Map.of() : resolveExternalSkuIds(channelProduct);
            String xmlPayload = lazadaPayloadBuilder.buildPayload(
                    product,
                    variants,
                    migratedImageUrls,
                    externalSkuIdBySku,
                    config,
                    isNew
            );

            Map<String, String> params = new HashMap<>();
            params.put("payload", xmlPayload);

            String apiPath = isNew ? "/product/create" : "/product/update";

            String responseStr = lazadaApiClient.executePost(channel.getId(), apiPath, params);
            JsonNode root = objectMapper.readTree(responseStr);

            if (root.has("code") && "0".equals(root.get("code").asText())) {
                JsonNode data = root.path("data");
                String itemId = data.path("item_id").asText(null);
                if (isNew && (itemId == null || itemId.isBlank())) {
                    throw new RuntimeException("Missing item_id in create response data");
                }

                if (itemId != null && !itemId.isBlank()) {
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
                String errorMsg = resolveLazadaErrorMessage(root);
                throw new RuntimeException("Lazada API returned error: " + errorMsg);
            }

        } catch (PlatformAccessTokenExpiredException e) {
            log.error("[LazadaSync] Token expired for channel {}", channel.getId(), e);
            throw e;
        } catch (Exception e) {
            log.error("[LazadaSync] Failed to sync product '{}' to Lazada: {}", product.getName(), e.getMessage(), e);
            channelProduct.setSyncStatus(SyncStatus.FAILED);
            channelProduct.setLastSyncError(e.getMessage());
            channelProductRepository.save(channelProduct);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private Map<String, String> resolveExternalSkuIds(ChannelProduct channelProduct) {
        return channelProductVariantRepository.findByChannelProductId(channelProduct.getId()).stream()
                .filter(mapping -> mapping.getVariant() != null)
                .filter(mapping -> mapping.getVariant().getSku() != null && !mapping.getVariant().getSku().isBlank())
                .filter(mapping -> mapping.getExternalVariantId() != null && !mapping.getExternalVariantId().isBlank())
                .collect(Collectors.toMap(
                        mapping -> mapping.getVariant().getSku(),
                        ChannelProductVariant::getExternalVariantId,
                        (first, ignored) -> first
                ));
    }

    private void migrateSizeChartImage(LazadaProductConfig config, UUID channelId) {
        if (config.getAttributes() == null || config.getAttributes().isEmpty()) return;
        Map<String, Object> attributes = new HashMap<>(config.getAttributes());
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            String normalized = entry.getKey() == null ? "" : entry.getKey().trim().toLowerCase()
                    .replace(' ', '_').replace('-', '_');
            if (!"size_chart".equals(normalized) && !"size_chart_image".equals(normalized)) continue;
            String sourceUrl = entry.getValue() == null ? null : String.valueOf(entry.getValue());
            if (sourceUrl == null || sourceUrl.isBlank()) return;
            entry.setValue(lazadaImageService.migrateImageUrl(sourceUrl, channelId));
            config.setAttributes(attributes);
            return;
        }
    }

    private LazadaProductConfig resolveProductConfig(ChannelProduct channelProduct) {
        if (channelProduct.getMetadata() == null || !(channelProduct.getMetadata().get("platformConfig") instanceof Map<?, ?> config)) {
            throw new IllegalStateException("Missing Lazada product configuration");
        }
        return objectMapper.convertValue(config, LazadaProductConfig.class);
    }

    private void validateSyncPrerequisites(Product product, List<ProductVariant> variants, LazadaProductConfig config) {
        if (config.getBrandId() == null || config.getBrandId().isBlank()) {
            throw new IllegalStateException("Missing Lazada brand configuration");
        }
        if (product.getWeightGrams() == null || product.getWeightGrams() <= 0) {
            throw new IllegalStateException("Missing package weight");
        }
        Map<String, Object> attributes = product.getAttributes();
        for (String key : List.of("packageWidthCm", "packageHeightCm", "packageLengthCm")) {
            Object value = attributes == null ? null : attributes.get(key);
            if (!isPositiveNumber(value)) {
                throw new IllegalStateException("Missing " + key);
            }
        }
        for (ProductVariant variant : variants) {
            if (!Boolean.FALSE.equals(variant.getIsActive())
                    && (variant.getPrice() == null || variant.getPrice().compareTo(BigDecimal.ZERO) <= 0)) {
                throw new IllegalStateException("Missing selling price for SKU " + variant.getSku());
            }
        }
    }

    private boolean isPositiveNumber(Object value) {
        if (value == null || value.toString().isBlank()) return false;
        try {
            return new BigDecimal(value.toString()).signum() > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String resolveLazadaErrorMessage(JsonNode root) {
        String message = root.path("message").asText("Unknown API error");
        JsonNode details = root.path("detail");
        if (details.isArray() && !details.isEmpty()) {
            String detailMessage = details.get(0).path("message").asText(null);
            String field = details.get(0).path("field").asText(null);
            if (detailMessage != null && !detailMessage.isBlank()) {
                return field == null || field.isBlank()
                        ? message + " - " + detailMessage
                        : message + " - " + field + ": " + detailMessage;
            }
        }
        return message;
    }
}
