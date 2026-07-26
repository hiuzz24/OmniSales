package fu.osms.sync.tiktok.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.enums.SyncStatus;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.sync.service.MarketplaceStockQuantityResolver;
import fu.osms.sync.tiktok.TikTokInventoryUpdateService;
import fu.osms.sync.tiktok.inventory.TikTokInventoryGateway;
import fu.osms.sync.tiktok.inventory.TikTokInventorySetCommand;
import fu.osms.sync.tiktok.inventory.TikTokInventoryTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TikTokInventoryUpdateServiceImpl implements TikTokInventoryUpdateService {

    private final ChannelRepository channelRepository;
    private final ChannelProductVariantRepository mappingRepository;
    private final MarketplaceStockQuantityResolver quantityResolver;
    private final TikTokInventoryGateway inventoryGateway;
    private final ChannelProductVariantRepository channelProductVariantRepository;
    private final MarketplaceStockQuantityResolver marketplaceStockQuantityResolver;
    private final TikTokAuthorizedApiClient tikTokApiClient;
    private final ObjectMapper objectMapper;

    @Override
    public int pushAvailableStock(UUID channelId) {
        return pushAvailableStock(channelId, null);
    }

    @Override
    public int pushAvailableStock(UUID channelId, Collection<UUID> variantIds) {
        Channel channel = channelRepository.findById(channelId)
                .filter(item -> item.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.CHANNEL_NOT_FOUND));
        if (channel.getPlatform() != PlatformType.TIKTOK) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Channel is not a TikTok Shop channel.");
        }

        String shopCipher = requireText(channel.getMetadata(), "shopCipher", "shop_cipher", "cipher");
        String configuredWarehouseId = optionalText(
                channel.getMetadata(), "tiktokWarehouseId", "defaultTikTokWarehouseId");
        Set<UUID> scopedVariantIds = sanitizeVariantIds(variantIds);
        List<ChannelProductVariant> mappings = scopedVariantIds.isEmpty()
                ? mappingRepository.findActiveByChannelIdWithVariant(channelId)
                : mappingRepository.findActiveByChannelIdAndVariantIdInWithVariant(
                        channelId, new ArrayList<>(scopedVariantIds));

        List<TikTokInventorySetCommand> commands = new ArrayList<>();
        List<ChannelProductVariant> pushedMappings = new ArrayList<>();
        for (ChannelProductVariant mapping : mappings) {
            String productId = mapping.getChannelProduct().getExternalProductId();
            String externalStatus = mapping.getChannelProduct().getExternalStatus();
            if (!"ACTIVATE".equalsIgnoreCase(externalStatus)
                    || !hasText(productId)
                    || !hasText(mapping.getExternalVariantId())) {
                continue;
            }

            List<String> warehouseIds = warehouseIds(mapping, configuredWarehouseId);
            if (warehouseIds.isEmpty()) {
                throw new AppException(
                        ErrorCode.INVALID_REQUEST,
                        "TikTok SKU " + mapping.getExternalSku()
                                + " has no warehouse mapping. Pull TikTok products before syncing inventory."
                );
            }
            String primaryWarehouseId = hasText(configuredWarehouseId)
                    && warehouseIds.contains(configuredWarehouseId)
                    ? configuredWarehouseId
                    : warehouseIds.get(0);
            commands.add(new TikTokInventorySetCommand(
                    new TikTokInventoryTarget(
                            productId,
                            mapping.getExternalVariantId(),
                            primaryWarehouseId,
                            List.copyOf(warehouseIds)
                    ),
                    quantityResolver.maxAvailableQuantityForSkuGroup(mapping)
            ));
            pushedMappings.add(mapping);
        }

        if (commands.isEmpty()) {
            return 0;
        }
        inventoryGateway.setAvailable(channelId, shopCipher, commands);

        OffsetDateTime syncedAt = OffsetDateTime.now();
        for (ChannelProductVariant mapping : pushedMappings) {
            mapping.setSyncStatus(SyncStatus.SYNCED);
            mapping.setLastSyncedAt(syncedAt);
            tikTokApiClient.updateInventory(channelId, shopCipher, entry.getKey(), skuPayloads);
            updatePrices(channelId, shopCipher, channel, entry.getKey(), entry.getValue());
            OffsetDateTime syncedAt = OffsetDateTime.now();
            for (ChannelProductVariant mapping : entry.getValue()) {
                if (mapping.getVariant() != null
                        && mapping.getVariant().getPrice() != null
                        && mapping.getVariant().getPrice().signum() > 0) {
                    mapping.setExternalPrice(mapping.getVariant().getPrice());
                }
                mapping.setSyncStatus(SyncStatus.SYNCED);
                mapping.setLastSyncedAt(syncedAt);
            }
            channelProductVariantRepository.saveAll(entry.getValue());
            pushedVariantCount += entry.getValue().size();
        }
        mappingRepository.saveAll(pushedMappings);
        return pushedMappings.size();
    }

    private void updatePrices(UUID channelId,
                              String shopCipher,
                              Channel channel,
                              String productId,
                              List<ChannelProductVariant> mappings) {
        List<Map<String, Object>> skus = mappings.stream()
                .filter(mapping -> mapping.getVariant() != null
                        && mapping.getVariant().getPrice() != null
                        && mapping.getVariant().getPrice().signum() > 0
                        && hasText(mapping.getExternalVariantId()))
                .map(mapping -> {
                    String amount = mapping.getVariant().getPrice().toPlainString();
                    return Map.<String, Object>of(
                            "id", mapping.getExternalVariantId(),
                            "price", Map.of(
                                    "amount", amount,
                                    "currency", currency(channel),
                                    "sale_price", amount
                            )
                    );
                })
                .toList();
        if (skus.isEmpty()) {
            return;
        }

        String response = tikTokApiClient.executePost(
                channelId,
                "/product/202309/products/" + productId + "/prices/update",
                Map.of("shop_cipher", shopCipher),
                serialize(Map.of("skus", skus))
        );
        ensureSuccess(response);
    }

    private Set<UUID> sanitizeVariantIds(Collection<UUID> variantIds) {
        if (variantIds == null || variantIds.isEmpty()) {
            return Set.of();
        }
        Set<UUID> result = new LinkedHashSet<>();
        for (UUID variantId : variantIds) {
            if (variantId != null) {
                result.add(variantId);
            }
        }
        return result;
    }

    private List<String> warehouseIds(
            ChannelProductVariant mapping,
            String configuredWarehouseId
    ) {
        Set<String> ids = new LinkedHashSet<>();
        if (mapping.getMetadata() != null
                && mapping.getMetadata().get("tiktokWarehouseIds") instanceof List<?> values) {
            values.stream()
                    .map(String::valueOf)
                    .filter(this::hasText)
                    .forEach(ids::add);
        }
        if (hasText(configuredWarehouseId)) {
            ids.add(configuredWarehouseId);
        }
        return new ArrayList<>(ids);
    }

    private String optionalText(Map<String, Object> metadata, String... keys) {
        if (metadata == null) {
            return null;
        }
        for (String key : keys) {
            Object value = metadata.get(key);
            if (value != null && hasText(value.toString())) {
                return value.toString();
            }
        }
        return null;
    }

    private String requireText(Map<String, Object> metadata, String... keys) {
        String value = optionalText(metadata, keys);
    private String currency(Channel channel) {
        String region = optionalText(channel.getMetadata(), "region", "sellerBaseRegion");
        return switch (region == null ? "VN" : region.toUpperCase()) {
            case "ID" -> "IDR";
            case "MY" -> "MYR";
            case "PH" -> "PHP";
            case "SG" -> "SGD";
            case "TH" -> "THB";
            default -> "VND";
        };
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Khong the tao payload cap nhat gia TikTok.", e);
        }
    }

    private void ensureSuccess(String response) {
        try {
            Map<?, ?> payload = objectMapper.readValue(response, Map.class);
            Object code = payload.get("code");
            if (code != null && !"0".equals(String.valueOf(code))) {
                throw new IllegalStateException(
                        "TikTok Shop price update failed: code=" + code + ", message=" + payload.get("message")
                );
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Khong doc duoc response cap nhat gia TikTok.", e);
        }
    }

    private UUID optionalUuid(Map<String, Object> metadata, String key) {
        String value = optionalText(metadata, key);
        if (!hasText(value)) {
            throw new AppException(
                    ErrorCode.INVALID_REQUEST,
                    "TikTok channel is missing shopCipher metadata."
            );
        }
        return value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank() && !"null".equalsIgnoreCase(value);
    }
}
