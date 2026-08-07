package fu.osms.sync.inventory.impl;

import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.common.enums.SyncStatus;
import fu.osms.sync.inventory.InventoryObservation;
import fu.osms.sync.inventory.InventoryReconciliationProperties;
import fu.osms.sync.inventory.InventoryReconciliationService;
import fu.osms.sync.service.MarketplaceStockQuantityResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryReconciliationServiceImpl implements InventoryReconciliationService {

    private final ChannelProductVariantRepository mappingRepository;
    private final MarketplaceStockQuantityResolver quantityResolver;
    private final InventoryReconciliationProperties properties;

    @Override
    @Transactional
    public void observe(InventoryObservation observation) {
        ChannelProductVariant mapping = mappingRepository.findByIdForUpdate(observation.mappingId())
                .orElse(null);
        if (mapping == null) {
            return;
        }
        Map<String, Object> existing = InventoryReconciliationMetadata.state(mapping);
        OffsetDateTime previousObservedAt =
                InventoryReconciliationMetadata.time(existing, "observedAt");
        if (previousObservedAt != null
                && observation.observedAt() != null
                && observation.observedAt().isBefore(previousObservedAt)) {
            return;
        }

        int localAvailable = quantityResolver
                .resolveAvailableByMappingIds(java.util.List.of(mapping.getId()))
                .getOrDefault(mapping.getId(), 0);
        if (localAvailable == observation.remoteAvailable()) {
            mapping.setSyncStatus(SyncStatus.SYNCED);
            mapping.setLastSyncedAt(OffsetDateTime.now());
            InventoryReconciliationMetadata.clear(mapping);
            mappingRepository.save(mapping);
            return;
        }

        String existingState = InventoryReconciliationMetadata.text(existing, "state");
        if ("FAILED".equals(existingState)) {
            existing.put("remoteAvailable", observation.remoteAvailable());
            existing.put("observedAt", observedAt(observation).toString());
            existing.put("externalStockLocation", observation.externalStockLocation());
            existing.put("webhookEventId", string(observation.webhookEventId()));
            InventoryReconciliationMetadata.write(mapping, existing);
            mapping.setSyncStatus(SyncStatus.OUT_OF_SYNC);
            mappingRepository.save(mapping);
            return;
        }

        Map<String, Object> next = new HashMap<>(existing);
        OffsetDateTime now = OffsetDateTime.now();
        boolean activeCycle = existingState != null && !"SYNCED".equals(existingState);
        next.put("state", "PENDING");
        next.putIfAbsent("step", "READ_REMOTE");
        next.put("remoteAvailable", observation.remoteAvailable());
        next.put("observedAt", observedAt(observation).toString());
        next.putIfAbsent("reconcileAfter",
                now.plusNanos(properties.getDebounceMs() * 1_000_000).toString());
        next.putIfAbsent("apiAttemptCount", 0);
        next.putIfAbsent("correctivePushCount", 0);
        next.putIfAbsent("cycleId", UUID.randomUUID().toString());
        next.put("externalStockLocation", observation.externalStockLocation());
        next.put("webhookEventId", string(observation.webhookEventId()));
        next.put("lastError", null);
        if (!activeCycle) {
            next.put("step", "READ_REMOTE");
        }

        InventoryReconciliationMetadata.write(mapping, next);
        mapping.setSyncStatus(SyncStatus.OUT_OF_SYNC);
        mappingRepository.save(mapping);
    }

    private OffsetDateTime observedAt(InventoryObservation observation) {
        return observation.observedAt() == null ? OffsetDateTime.now() : observation.observedAt();
    }

    private String string(Object value) {
        return value == null ? null : value.toString();
    }
}
