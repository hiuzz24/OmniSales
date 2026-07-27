package fu.osms.sync.inventory.impl;

import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.common.enums.SyncStatus;
import fu.osms.sync.inventory.InventoryReconciliationProperties;
import fu.osms.sync.inventory.InventoryReconciliationTask;
import fu.osms.sync.inventory.InventoryReconciliationTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryReconciliationTaskServiceImpl implements InventoryReconciliationTaskService {

    private final ChannelProductVariantRepository mappingRepository;
    private final InventoryReconciliationProperties properties;

    @Override
    @Transactional
    public List<InventoryReconciliationTask> claimDueTasks() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime cutoff = now.minusNanos(properties.getProcessingTimeoutMs() * 1_000_000);
        List<UUID> ids = mappingRepository.claimDueInventoryReconciliationIds(
                cutoff, properties.getBatchSize());
        if (ids.isEmpty()) {
            return List.of();
        }

        List<InventoryReconciliationTask> tasks = new ArrayList<>();
        for (ChannelProductVariant mapping :
                mappingRepository.findAllWithChannelAndVariantByIdIn(ids)) {
            Map<String, Object> metadata = InventoryReconciliationMetadata.state(mapping);
            String originalState = InventoryReconciliationMetadata.text(metadata, "state");
            String step = InventoryReconciliationMetadata.text(metadata, "step");
            if ("VERIFYING".equals(originalState)) {
                step = "VERIFY_REMOTE";
            }
            metadata.put("state", "PROCESSING");
            metadata.put("step", step == null ? "READ_REMOTE" : step);
            metadata.put("processingStartedAt", now.toString());
            InventoryReconciliationMetadata.write(mapping, metadata);
            mappingRepository.save(mapping);
            tasks.add(new InventoryReconciliationTask(
                    mapping.getId(),
                    mapping.getChannelProduct().getChannel().getId(),
                    mapping.getChannelProduct().getChannel().getPlatform(),
                    InventoryReconciliationMetadata.text(metadata, "cycleId"),
                    InventoryReconciliationMetadata.text(metadata, "step"),
                    InventoryReconciliationMetadata.integer(metadata, "apiAttemptCount"),
                    InventoryReconciliationMetadata.integer(metadata, "correctivePushCount"),
                    InventoryReconciliationMetadata.text(metadata, "externalStockLocation")
            ));
        }
        return tasks;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSynced(UUID mappingId, String cycleId) {
        update(mappingId, cycleId, mapping -> {
            mapping.setSyncStatus(SyncStatus.SYNCED);
            mapping.setLastSyncedAt(OffsetDateTime.now());
            InventoryReconciliationMetadata.clear(mapping);
        });
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void scheduleVerification(UUID mappingId, String cycleId) {
        update(mappingId, cycleId, mapping -> {
            Map<String, Object> metadata = InventoryReconciliationMetadata.state(mapping);
            metadata.put("state", "VERIFYING");
            metadata.put("step", "VERIFY_REMOTE");
            metadata.put("apiAttemptCount", 0);
            metadata.put("correctivePushCount",
                    InventoryReconciliationMetadata.integer(metadata, "correctivePushCount") + 1);
            metadata.put("reconcileAfter", OffsetDateTime.now()
                    .plusNanos(properties.getVerificationDelayMs() * 1_000_000).toString());
            metadata.remove("processingStartedAt");
            metadata.put("lastError", null);
            InventoryReconciliationMetadata.write(mapping, metadata);
            mapping.setSyncStatus(SyncStatus.OUT_OF_SYNC);
        });
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void scheduleRetry(
            UUID mappingId,
            String cycleId,
            int nextAttempt,
            String error
    ) {
        update(mappingId, cycleId, mapping -> {
            Map<String, Object> metadata = InventoryReconciliationMetadata.state(mapping);
            long delayMs = nextAttempt <= 1 ? 30_000 : 120_000;
            metadata.put("state", "PENDING");
            metadata.put("apiAttemptCount", nextAttempt);
            metadata.put("reconcileAfter", OffsetDateTime.now()
                    .plusNanos(delayMs * 1_000_000).toString());
            metadata.remove("processingStartedAt");
            metadata.put("lastError", clean(error));
            InventoryReconciliationMetadata.write(mapping, metadata);
            mapping.setSyncStatus(SyncStatus.OUT_OF_SYNC);
        });
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID mappingId, String cycleId, String error) {
        update(mappingId, cycleId, mapping -> {
            Map<String, Object> metadata = InventoryReconciliationMetadata.state(mapping);
            metadata.put("state", "FAILED");
            metadata.remove("processingStartedAt");
            metadata.put("lastError", clean(error));
            InventoryReconciliationMetadata.write(mapping, metadata);
            mapping.setSyncStatus(SyncStatus.OUT_OF_SYNC);
        });
    }

    private void update(
            UUID mappingId,
            String cycleId,
            java.util.function.Consumer<ChannelProductVariant> updater
    ) {
        ChannelProductVariant mapping = mappingRepository.findByIdForUpdate(mappingId)
                .orElse(null);
        if (mapping == null) {
            return;
        }
        Map<String, Object> metadata = InventoryReconciliationMetadata.state(mapping);
        if (!java.util.Objects.equals(
                cycleId,
                InventoryReconciliationMetadata.text(metadata, "cycleId"))) {
            return;
        }
        updater.accept(mapping);
        mappingRepository.save(mapping);
    }

    private String clean(String error) {
        if (error == null || error.isBlank()) {
            return "Inventory reconciliation failed";
        }
        return error.length() <= 1000 ? error : error.substring(0, 1000);
    }
}
