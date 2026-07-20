package fu.osms.sync.tiktok.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.enums.SyncStatus;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.sync.tiktok.TikTokAuthorizedApiClient;
import fu.osms.sync.tiktok.TikTokInventoryUpdateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TikTokInventoryUpdateServiceImpl implements TikTokInventoryUpdateService {

    private final ChannelRepository channelRepository;
    private final ChannelProductVariantRepository channelProductVariantRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final TikTokAuthorizedApiClient tikTokApiClient;

    @Override
    @Transactional
    public int pushAvailableStock(UUID channelId) {
        return pushAvailableStock(channelId, null);
    }

    @Override
    @Transactional
    public int pushAvailableStock(UUID channelId, Collection<UUID> variantIds) {
        Channel channel = channelRepository.findById(channelId)
                .filter(item -> item.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.CHANNEL_NOT_FOUND));
        if (channel.getPlatform() != PlatformType.TIKTOK) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Kênh không phải TikTok Shop.");
        }

        String shopCipher = requireText(channel.getMetadata(), "shopCipher", "shop_cipher", "cipher");
        UUID defaultWarehouseId = optionalUuid(channel.getMetadata(), "defaultWarehouseId");
        String configuredTikTokWarehouseId = optionalText(channel.getMetadata(), "tiktokWarehouseId", "defaultTikTokWarehouseId");

        Set<UUID> scopedVariantIds = sanitizeVariantIds(variantIds);
        List<ChannelProductVariant> mappings = scopedVariantIds.isEmpty()
                ? channelProductVariantRepository.findActiveByChannelIdWithVariant(channelId)
                : channelProductVariantRepository.findActiveByChannelIdAndVariantIdInWithVariant(
                        channelId,
                        new ArrayList<>(scopedVariantIds)
                );
        Map<String, List<ChannelProductVariant>> mappingsByProductId = new LinkedHashMap<>();
        for (ChannelProductVariant mapping : mappings) {
            String productId = mapping.getChannelProduct().getExternalProductId();
            String externalStatus = mapping.getChannelProduct().getExternalStatus();
            if ("ACTIVATE".equalsIgnoreCase(externalStatus)
                    && hasText(productId)
                    && hasText(mapping.getExternalVariantId())) {
                mappingsByProductId.computeIfAbsent(productId, ignored -> new ArrayList<>()).add(mapping);
            }
        }

        int pushedVariantCount = 0;
        for (Map.Entry<String, List<ChannelProductVariant>> entry : mappingsByProductId.entrySet()) {
            List<Map<String, Object>> skuPayloads = new ArrayList<>();
            for (ChannelProductVariant mapping : entry.getValue()) {
                int availableQuantity = availableQuantity(mapping, defaultWarehouseId);
                List<String> warehouseIds = warehouseIds(mapping, configuredTikTokWarehouseId);
                if (warehouseIds.isEmpty()) {
                    throw new AppException(
                            ErrorCode.INVALID_REQUEST,
                            "SKU " + mapping.getExternalSku() + " chưa có TikTok warehouse ID. Hãy đồng bộ từ TikTok về trước."
                    );
                }

                String primaryWarehouseId = hasText(configuredTikTokWarehouseId)
                        && warehouseIds.contains(configuredTikTokWarehouseId)
                        ? configuredTikTokWarehouseId
                        : warehouseIds.get(0);
                List<Map<String, Object>> inventory = warehouseIds.stream()
                        .map(warehouseId -> Map.<String, Object>of(
                                "warehouse_id", warehouseId,
                                "quantity", warehouseId.equals(primaryWarehouseId) ? availableQuantity : 0
                        ))
                        .toList();
                skuPayloads.add(Map.of("id", mapping.getExternalVariantId(), "inventory", inventory));
            }

            tikTokApiClient.updateInventory(channelId, shopCipher, entry.getKey(), skuPayloads);
            OffsetDateTime syncedAt = OffsetDateTime.now();
            for (ChannelProductVariant mapping : entry.getValue()) {
                mapping.setSyncStatus(SyncStatus.SYNCED);
                mapping.setLastSyncedAt(syncedAt);
            }
            channelProductVariantRepository.saveAll(entry.getValue());
            pushedVariantCount += entry.getValue().size();
        }
        return pushedVariantCount;
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

    private int availableQuantity(ChannelProductVariant mapping, UUID defaultWarehouseId) {
        List<InventoryItem> inventoryItems = inventoryItemRepository.findByVariantIdIn(List.of(mapping.getVariant().getId()));
        return inventoryItems.stream()
                .filter(item -> defaultWarehouseId == null
                        || (item.getWarehouse() != null && defaultWarehouseId.equals(item.getWarehouse().getId())))
                .mapToInt(item -> Math.max(0,
                        (item.getQuantityOnHand() == null ? 0 : item.getQuantityOnHand())
                                - (item.getReservedQuantity() == null ? 0 : item.getReservedQuantity())))
                .sum();
    }

    private List<String> warehouseIds(ChannelProductVariant mapping, String configuredWarehouseId) {
        Set<String> ids = new LinkedHashSet<>();
        if (mapping.getMetadata() != null && mapping.getMetadata().get("tiktokWarehouseIds") instanceof List<?> values) {
            values.stream().map(String::valueOf).filter(this::hasText).forEach(ids::add);
        }
        if (hasText(configuredWarehouseId)) {
            ids.add(configuredWarehouseId);
        }
        return new ArrayList<>(ids);
    }

    private String requireText(Map<String, Object> metadata, String... keys) {
        String value = optionalText(metadata, keys);
        if (!hasText(value)) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Kênh TikTok thiếu shopCipher. Hãy cập nhật cấu hình kênh trước khi đồng bộ.");
        }
        return value;
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

    private UUID optionalUuid(Map<String, Object> metadata, String key) {
        String value = optionalText(metadata, key);
        if (!hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.INVALID_REQUEST, key + " không hợp lệ.");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank() && !"null".equalsIgnoreCase(value);
    }
}
