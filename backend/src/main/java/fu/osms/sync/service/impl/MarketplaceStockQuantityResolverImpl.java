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
import java.util.HashMap;
import java.util.Map;
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
        if (mapping == null || mapping.getId() == null) {
            return 0;
        }
        return resolveAvailableByMappingIds(List.of(mapping.getId()))
                .getOrDefault(mapping.getId(), 0);
    }

    @Override
    public Map<UUID, Integer> resolveAvailableByMappingIds(Collection<UUID> mappingIds) {
        List<UUID> ids = mappingIds == null
                ? List.of()
                : mappingIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }

        List<ChannelProductVariant> mappings =
                channelProductVariantRepository.findAllWithChannelAndVariantByIdIn(ids);
        Set<String> skuKeys = mappings.stream()
                .map(this::skuKey)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<ChannelProductVariant> groupedMappings = skuKeys.isEmpty()
                ? List.of()
                : channelProductVariantRepository.findActiveByNormalizedExternalSkuInWithVariant(
                        new ArrayList<>(skuKeys));

        Map<String, Set<UUID>> variantIdsBySku = new HashMap<>();
        for (ChannelProductVariant grouped : groupedMappings) {
            String key = skuKey(grouped);
            if (key != null && grouped.getVariant() != null && grouped.getVariant().getId() != null) {
                variantIdsBySku.computeIfAbsent(key, ignored -> new LinkedHashSet<>())
                        .add(grouped.getVariant().getId());
            }
        }

        Set<UUID> allVariantIds = new LinkedHashSet<>();
        for (ChannelProductVariant mapping : mappings) {
            String key = skuKey(mapping);
            Set<UUID> grouped = key == null ? null : variantIdsBySku.get(key);
            if (grouped != null && !grouped.isEmpty()) {
                allVariantIds.addAll(grouped);
            } else if (mapping.getVariant() != null && mapping.getVariant().getId() != null) {
                allVariantIds.add(mapping.getVariant().getId());
            }
        }
        Map<UUID, List<InventoryItem>> inventoryByVariant = inventoryItemRepository
                .findByVariantIdIn(allVariantIds).stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        item -> item.getVariant().getId()));

        Map<UUID, Integer> result = new HashMap<>();
        for (ChannelProductVariant mapping : mappings) {
            Set<UUID> scopedVariantIds = variantIdsBySku.getOrDefault(
                    skuKey(mapping),
                    mapping.getVariant() == null || mapping.getVariant().getId() == null
                            ? Set.of()
                            : Set.of(mapping.getVariant().getId())
            );
            UUID warehouseId = defaultWarehouseId(mapping);
            int available = scopedVariantIds.stream()
                    .flatMap(variantId -> inventoryByVariant.getOrDefault(variantId, List.of()).stream())
                    .filter(item -> warehouseId == null
                            || (item.getWarehouse() != null
                            && warehouseId.equals(item.getWarehouse().getId())))
                    .mapToInt(this::availableQuantity)
                    .max()
                    .orElse(0);
            result.put(mapping.getId(), available);
        }
        return result;
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
