package fu.osms.sync.service.impl;

import fu.osms.catalog.entity.Product;
import fu.osms.catalog.repository.ProductRepository;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelProduct;
import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.repository.ChannelProductRepository;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.enums.SyncStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChannelProductAggregationService {

    private static final String ACTIVE = "ACTIVE";
    private static final String ARCHIVED = "ARCHIVED";

    private final ChannelProductRepository channelProductRepository;
    private final ChannelProductVariantRepository channelProductVariantRepository;
    private final ProductRepository productRepository;

    public ChannelProduct normalizeImportedMapping(ChannelProduct importedMapping) {
        if (importedMapping == null
                || importedMapping.getChannel() == null
                || importedMapping.getChannel().getId() == null
                || importedMapping.getProduct() == null
                || importedMapping.getProduct().getId() == null) {
            return importedMapping;
        }

        stampPrimaryWarehouseMetadata(importedMapping);
        ChannelProduct saved = channelProductRepository.save(importedMapping);
        ChannelProduct canonical = mergeDuplicateActiveMappings(saved);
        updateProductMarketplaceWarehouseMetadata(canonical);
        return canonical;
    }

    private ChannelProduct mergeDuplicateActiveMappings(ChannelProduct importedMapping) {
        List<ChannelProduct> activeMappings = channelProductRepository.findByChannelIdAndProductIdAndMappingStateWithRefs(
                importedMapping.getChannel().getId(),
                importedMapping.getProduct().getId(),
                ACTIVE
        );
        if (activeMappings.size() <= 1) {
            return importedMapping;
        }

        ChannelProduct canonical = activeMappings.stream()
                .filter(mapping -> mapping.getCreatedAt() != null)
                .min((left, right) -> left.getCreatedAt().compareTo(right.getCreatedAt()))
                .orElse(activeMappings.get(0));
        stampPrimaryWarehouseMetadata(canonical);

        Set<String> mergedExternalProductIds = new LinkedHashSet<>();
        if (hasText(canonical.getExternalProductId())) {
            mergedExternalProductIds.add(canonical.getExternalProductId());
        }

        for (ChannelProduct duplicate : activeMappings) {
            if (Objects.equals(duplicate.getId(), canonical.getId())) {
                continue;
            }
            if (hasText(duplicate.getExternalProductId())) {
                mergedExternalProductIds.add(duplicate.getExternalProductId());
            }
            mergeVariantMappings(canonical, duplicate);
            archiveDuplicateMapping(canonical, duplicate);
        }

        Map<String, Object> canonicalMetadata = mutableMap(canonical.getMetadata());
        canonicalMetadata.put("mergedExternalProductIds", mergedExternalProductIds.stream().toList());
        canonicalMetadata.put("lastAggregatedAt", OffsetDateTime.now().toString());
        canonical.setMetadata(canonicalMetadata);
        return channelProductRepository.save(canonical);
    }

    private void mergeVariantMappings(ChannelProduct canonical, ChannelProduct duplicate) {
        List<ChannelProductVariant> canonicalVariants = channelProductVariantRepository.findByChannelProductId(canonical.getId());
        Map<String, ChannelProductVariant> byExternalVariantId = canonicalVariants.stream()
                .filter(mapping -> hasText(mapping.getExternalVariantId()))
                .collect(Collectors.toMap(
                        ChannelProductVariant::getExternalVariantId,
                        Function.identity(),
                        (left, right) -> left
                ));
        Map<UUID, ChannelProductVariant> byLocalVariantId = canonicalVariants.stream()
                .filter(mapping -> mapping.getVariant() != null && mapping.getVariant().getId() != null)
                .collect(Collectors.toMap(
                        mapping -> mapping.getVariant().getId(),
                        Function.identity(),
                        (left, right) -> left
                ));

        for (ChannelProductVariant duplicateVariant : channelProductVariantRepository.findByChannelProductId(duplicate.getId())) {
            ChannelProductVariant existing = hasText(duplicateVariant.getExternalVariantId())
                    ? byExternalVariantId.get(duplicateVariant.getExternalVariantId())
                    : null;
            if (existing == null && duplicateVariant.getVariant() != null) {
                existing = byLocalVariantId.get(duplicateVariant.getVariant().getId());
            }

            if (existing != null) {
                mergeVariantMetadata(existing, duplicateVariant, duplicate);
                continue;
            }

            duplicateVariant.setChannelProduct(canonical);
            Map<String, Object> metadata = mutableMap(duplicateVariant.getMetadata());
            metadata.put("mergedFromChannelProductId", duplicate.getId().toString());
            metadata.put("mergedAt", OffsetDateTime.now().toString());
            duplicateVariant.setMetadata(metadata);
            channelProductVariantRepository.save(duplicateVariant);
            if (hasText(duplicateVariant.getExternalVariantId())) {
                byExternalVariantId.put(duplicateVariant.getExternalVariantId(), duplicateVariant);
            }
            if (duplicateVariant.getVariant() != null && duplicateVariant.getVariant().getId() != null) {
                byLocalVariantId.put(duplicateVariant.getVariant().getId(), duplicateVariant);
            }
        }
    }

    private void mergeVariantMetadata(ChannelProductVariant existing,
                                      ChannelProductVariant duplicate,
                                      ChannelProduct duplicateChannelProduct) {
        Map<String, Object> metadata = mutableMap(existing.getMetadata());
        Map<String, Object> duplicateMetadata = mutableMap(duplicate.getMetadata());
        if (!duplicateMetadata.isEmpty()) {
            metadata.putIfAbsent("mergedDuplicateVariantMetadata", duplicateMetadata);
        }
        metadata.put("lastMergedDuplicateVariantId", duplicate.getId().toString());
        metadata.put("lastMergedDuplicateChannelProductId", duplicateChannelProduct.getId().toString());
        metadata.put("lastMergedAt", OffsetDateTime.now().toString());
        existing.setMetadata(metadata);
        existing.setSyncStatus(SyncStatus.SYNCED);
        existing.setLastSyncedAt(OffsetDateTime.now());
        channelProductVariantRepository.save(existing);
    }

    private void archiveDuplicateMapping(ChannelProduct canonical, ChannelProduct duplicate) {
        Map<String, Object> metadata = mutableMap(duplicate.getMetadata());
        metadata.put("mergedIntoChannelProductId", canonical.getId().toString());
        metadata.put("mergedIntoExternalProductId", canonical.getExternalProductId());
        metadata.put("mergedAt", OffsetDateTime.now().toString());
        metadata.put("mergeReason", "SAME_CHANNEL_PRODUCT");
        duplicate.setMetadata(metadata);
        duplicate.setMappingState(ARCHIVED);
        duplicate.setSyncStatus(SyncStatus.SYNCED);
        duplicate.setLastSyncError(null);
        duplicate.setLastSyncedAt(OffsetDateTime.now());
        channelProductRepository.save(duplicate);
    }

    @SuppressWarnings("unchecked")
    private void updateProductMarketplaceWarehouseMetadata(ChannelProduct mapping) {
        Product product = mapping.getProduct();
        Channel channel = mapping.getChannel();
        if (product == null || channel == null || channel.getPlatform() == null) {
            return;
        }

        Map<String, Object> attributes = mutableMap(product.getAttributes());
        Map<String, Object> warehouses = attributes.get("marketplacePrimaryWarehouses") instanceof Map<?, ?> existing
                ? new LinkedHashMap<>((Map<String, Object>) existing)
                : new LinkedHashMap<>();
        warehouses.put(channel.getPlatform().name(), primaryWarehouseMetadata(channel));
        attributes.put("marketplacePrimaryWarehouses", warehouses);
        product.setAttributes(attributes);
        productRepository.save(product);
    }

    private void stampPrimaryWarehouseMetadata(ChannelProduct mapping) {
        Channel channel = mapping.getChannel();
        Map<String, Object> metadata = mutableMap(mapping.getMetadata());
        metadata.put("sourcePlatform", channel.getPlatform().name());
        metadata.put("primaryPlatform", channel.getPlatform().name());
        metadata.put("primaryChannelId", channel.getId().toString());
        metadata.put("primaryWarehouse", primaryWarehouseMetadata(channel));
        mapping.setMetadata(metadata);
    }

    private Map<String, Object> primaryWarehouseMetadata(Channel channel) {
        Map<String, Object> channelMetadata = mutableMap(channel.getMetadata());
        Map<String, Object> result = new LinkedHashMap<>();
        putIfPresent(result, "defaultWarehouseId", text(channelMetadata.get("defaultWarehouseId")));
        putIfPresent(result, "externalWarehouseId", externalWarehouseId(channel.getPlatform(), channelMetadata));
        putIfPresent(result, "externalWarehouseKey", externalWarehouseKey(channel.getPlatform()));
        putIfPresent(result, "address", primaryWarehouseAddress(channel.getPlatform(), channelMetadata));
        result.put("channelId", channel.getId().toString());
        result.put("platform", channel.getPlatform().name());
        result.put("updatedAt", OffsetDateTime.now().toString());
        return result;
    }

    private String externalWarehouseId(PlatformType platform, Map<String, Object> metadata) {
        return switch (platform) {
            case SHOPIFY -> firstNonBlank(text(metadata.get("shopifyLocationId")), text(metadata.get("defaultShopifyLocationId")));
            case LAZADA -> firstNonBlank(text(metadata.get("lazadaWarehouseCode")), text(metadata.get("defaultLazadaWarehouseCode")));
            case TIKTOK -> firstNonBlank(text(metadata.get("tiktokWarehouseId")), text(metadata.get("defaultTikTokWarehouseId")));
            default -> null;
        };
    }

    private String externalWarehouseKey(PlatformType platform) {
        return switch (platform) {
            case SHOPIFY -> "shopifyLocationId";
            case LAZADA -> "lazadaWarehouseCode";
            case TIKTOK -> "tiktokWarehouseId";
            default -> null;
        };
    }

    private String primaryWarehouseAddress(PlatformType platform, Map<String, Object> metadata) {
        return switch (platform) {
            case SHOPIFY -> text(metadata.get("shopifyPrimaryWarehouseAddress"));
            case LAZADA -> text(metadata.get("lazadaPrimaryWarehouseAddress"));
            case TIKTOK -> text(metadata.get("tiktokPrimaryWarehouseAddress"));
            default -> null;
        };
    }

    private Map<String, Object> mutableMap(Map<String, Object> source) {
        return source == null ? new HashMap<>() : new HashMap<>(source);
    }

    private void putIfPresent(Map<String, Object> target, String key, String value) {
        if (hasText(value)) {
            target.put(key, value);
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String text(Object value) {
        return value == null ? null : value.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank() && !"null".equalsIgnoreCase(value);
    }
}
