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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

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

        List<ChannelProductVariant> seedMappings = channelProductVariantRepository
                .findActiveByVariantIdInWithChannel(new ArrayList<>(scopedVariantIds));
        Set<String> skuKeys = new LinkedHashSet<>();
        for (ChannelProductVariant mapping : seedMappings) {
            String skuKey = skuKey(mapping);
            if (skuKey != null) {
                skuKeys.add(skuKey);
            }
        }
        if (skuKeys.isEmpty()) {
            return scopedVariantIds;
        }

        Set<UUID> result = new LinkedHashSet<>(scopedVariantIds);
        channelProductVariantRepository.findActiveByNormalizedSkuInWithVariant(new ArrayList<>(skuKeys))
                .stream()
                .map(ChannelProductVariant::getVariant)
                .filter(Objects::nonNull)
                .map(variant -> variant.getId())
                .filter(Objects::nonNull)
                .forEach(result::add);
        return result;
    }

    @Override
    public int maxAvailableQuantityForSkuGroup(ChannelProductVariant mapping) {
        String skuKey = skuKey(mapping);
        if (skuKey == null) {
            if (mapping == null || mapping.getVariant() == null || mapping.getVariant().getId() == null) {
                return 0;
            }
            return maxAvailableQuantity(List.of(mapping.getVariant().getId()));
        }

        List<UUID> variantIds = channelProductVariantRepository
                .findActiveByNormalizedSkuInWithVariant(List.of(skuKey))
                .stream()
                .map(ChannelProductVariant::getVariant)
                .filter(Objects::nonNull)
                .map(variant -> variant.getId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return maxAvailableQuantity(variantIds);
    }

    private int maxAvailableQuantity(Collection<UUID> variantIds) {
        Set<UUID> sanitized = sanitizeVariantIds(variantIds);
        if (sanitized.isEmpty()) {
            return 0;
        }
        return inventoryItemRepository.findByVariantIdIn(sanitized)
                .stream()
                .mapToInt(this::availableQuantity)
                .max()
                .orElse(0);
    }

    private int availableQuantity(InventoryItem item) {
        if (item.getAvailableQuantity() != null) {
            return Math.max(item.getAvailableQuantity(), 0);
        }
        int quantityOnHand = item.getQuantityOnHand() == null ? 0 : item.getQuantityOnHand();
        int reservedQuantity = item.getReservedQuantity() == null ? 0 : item.getReservedQuantity();
        return Math.max(quantityOnHand - reservedQuantity, 0);
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
        if (mapping == null) {
            return null;
        }
        String value = firstNonBlank(
                mapping.getExternalSku(),
                mapping.getVariant() == null ? null : mapping.getVariant().getSku()
        );
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
