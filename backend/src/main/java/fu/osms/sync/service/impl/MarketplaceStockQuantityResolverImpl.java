package fu.osms.sync.service.impl;

import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.sync.service.MarketplaceStockQuantityResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MarketplaceStockQuantityResolverImpl implements MarketplaceStockQuantityResolver {

    private final ChannelProductVariantRepository channelProductVariantRepository;
    private final InventoryItemRepository inventoryItemRepository;

    @Override
    public Set<UUID> expandVariantIdsBySkuGroup(Collection<UUID> variantIds) {
        Set<UUID> scopedVariantIds = sanitizeVariantIds(variantIds);
        if (scopedVariantIds.isEmpty()) {
            return Set.of();
        }

        Set<UUID> result = new LinkedHashSet<>(scopedVariantIds);
        for (UUID variantId : scopedVariantIds) {
            Set<String> externalSkuKeys = channelProductVariantRepository
                    .findActiveByVariantIdWithChannel(variantId).stream()
                    .map(this::skuKey)
                    .filter(Objects::nonNull)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            if (externalSkuKeys.size() != 1) {
                continue;
            }
            channelProductVariantRepository.findActiveByNormalizedExternalSkuInWithVariant(
                            new ArrayList<>(externalSkuKeys)).stream()
                    .map(ChannelProductVariant::getVariant)
                    .filter(Objects::nonNull)
                    .map(variant -> variant.getId())
                    .filter(Objects::nonNull)
                    .forEach(result::add);
        }
        return result;
    }

    @Override
    public int maxAvailableQuantityForSkuGroup(ChannelProductVariant mapping) {
        String skuKey = skuKey(mapping);
        if (skuKey == null) {
            if (mapping == null || mapping.getVariant() == null || mapping.getVariant().getId() == null) {
                return 0;
            }
            return maxAvailableQuantity(
                    List.of(mapping.getVariant().getId()),
                    defaultWarehouseId(mapping));
        }

        List<UUID> variantIds = channelProductVariantRepository
                .findActiveByNormalizedExternalSkuInWithVariant(List.of(skuKey))
                .stream()
                .map(ChannelProductVariant::getVariant)
                .filter(Objects::nonNull)
                .map(variant -> variant.getId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return maxAvailableQuantity(variantIds, defaultWarehouseId(mapping));
    }

    private int maxAvailableQuantity(
            Collection<UUID> variantIds,
            UUID defaultWarehouseId) {
        Set<UUID> sanitized = sanitizeVariantIds(variantIds);
        if (sanitized.isEmpty()) {
            return 0;
        }
        Map<String, List<InventoryItem>> itemsByWarehouse = inventoryItemRepository.findByVariantIdIn(sanitized)
                .stream()
                .filter(item -> defaultWarehouseId == null
                        || (item.getWarehouse() != null
                        && defaultWarehouseId.equals(item.getWarehouse().getId())))
                .collect(Collectors.groupingBy(
                        this::warehouseKey,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        return itemsByWarehouse.values().stream()
                .mapToInt(this::sellableQuantityForSharedWarehouseStock)
                .sum();
    }

    private UUID defaultWarehouseId(ChannelProductVariant mapping) {
        if (mapping == null
                || mapping.getChannelProduct() == null
                || mapping.getChannelProduct().getChannel() == null
                || mapping.getChannelProduct().getChannel().getMetadata() == null) {
            return null;
        }
        Object value = mapping.getChannelProduct().getChannel()
                .getMetadata().get("defaultWarehouseId");
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.toString().trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private int availableQuantity(InventoryItem item) {
        if (item.getAvailableQuantity() != null) {
            return Math.max(item.getAvailableQuantity(), 0);
        }
        return Math.max(safeInt(item.getQuantityOnHand()) - safeInt(item.getReservedQuantity()), 0);
    }

    private int sellableQuantityForSharedWarehouseStock(List<InventoryItem> items) {
        int quantityOnHand = items.stream()
                .mapToInt(item -> safeInt(item.getQuantityOnHand()))
                .max()
                .orElse(0);
        int reservedQuantity = items.stream()
                .mapToInt(item -> safeInt(item.getReservedQuantity()))
                .sum();
        int computedAvailable = quantityOnHand - reservedQuantity;
        if (items.size() <= 1) {
            return Math.max(availableQuantity(items.get(0)), 0);
        }
        return Math.max(computedAvailable, 0);
    }

    private String warehouseKey(InventoryItem item) {
        if (item.getWarehouse() != null && item.getWarehouse().getId() != null) {
            return "warehouse:" + item.getWarehouse().getId();
        }
        return "item:" + item.getId();
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
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

    private String skuKey(ChannelProductVariant mapping) {
        if (mapping == null || mapping.getExternalSku() == null
                || mapping.getExternalSku().isBlank()) {
            return null;
        }
        return mapping.getExternalSku().trim().toLowerCase(Locale.ROOT);
    }
}
