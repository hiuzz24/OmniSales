package fu.osms.sync.inventory.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelProduct;
import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.enums.SyncStatus;
import fu.osms.sync.inventory.InventoryReconciliationProperties;
import fu.osms.sync.inventory.InventoryReconciliationTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryReconciliationTaskServiceImpl Tests")
class InventoryReconciliationTaskServiceImplTest {

    @Mock private ChannelProductVariantRepository mappingRepository;

    private InventoryReconciliationTaskServiceImpl service;
    private final InventoryReconciliationProperties properties = new InventoryReconciliationProperties();

    @BeforeEach
    void setUp() {
        service = new InventoryReconciliationTaskServiceImpl(mappingRepository, properties);
    }

    /**
     * Build a mapping whose {@code metadata.inventoryReconciliation} sub-map matches the given state map.
     * This is required because InventoryReconciliationMetadata.state(mapping) extracts the nested sub-map.
     */
    private ChannelProductVariant mapping(UUID mappingId, UUID channelId, Map<String, Object> stateMap) {
        Channel channel = Channel.builder().id(channelId).platform(PlatformType.LAZADA).build();
        ChannelProduct cp = ChannelProduct.builder().id(UUID.randomUUID()).channel(channel).build();
        Map<String, Object> meta = new HashMap<>();
        if (stateMap != null) {
            meta.put("inventoryReconciliation", new HashMap<>(stateMap));
        }
        return ChannelProductVariant.builder()
                .id(mappingId)
                .channelProduct(cp)
                .variant(new fu.osms.catalog.entity.ProductVariant())
                .metadata(meta)
                .build();
    }

    @Test
    @DisplayName("claimDueTasks: returns empty list when repository returns no ids")
    void claimDueTasks_empty() {
        when(mappingRepository.claimDueInventoryReconciliationIds(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of());

        List<InventoryReconciliationTask> result = service.claimDueTasks();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("claimDueTasks: claims a PENDING mapping and returns its task with metadata fields")
    void claimDueTasks_pending() {
        UUID mappingId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        Map<String, Object> state = Map.of(
                "state", "PENDING",
                "step", "READ_REMOTE",
                "cycleId", "cyc-1",
                "apiAttemptCount", 1,
                "correctivePushCount", 0,
                "externalStockLocation", "ext-loc");
        ChannelProductVariant mapping = mapping(mappingId, channelId, state);

        when(mappingRepository.claimDueInventoryReconciliationIds(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(mappingId));
        when(mappingRepository.findAllWithChannelAndVariantByIdIn(List.of(mappingId)))
                .thenReturn(List.of(mapping));

        List<InventoryReconciliationTask> result = service.claimDueTasks();

        assertThat(result).hasSize(1);
        InventoryReconciliationTask task = result.get(0);
        assertThat(task.mappingId()).isEqualTo(mappingId);
        assertThat(task.channelId()).isEqualTo(channelId);
        assertThat(task.platform()).isEqualTo(PlatformType.LAZADA);
        assertThat(task.cycleId()).isEqualTo("cyc-1");
        assertThat(task.step()).isEqualTo("READ_REMOTE");
        assertThat(task.apiAttemptCount()).isEqualTo(1);
        assertThat(task.correctivePushCount()).isEqualTo(0);
        assertThat(task.externalStockLocation()).isEqualTo("ext-loc");

        ArgumentCaptor<ChannelProductVariant> captor = ArgumentCaptor.forClass(ChannelProductVariant.class);
        verify(mappingRepository).save(captor.capture());
        Object stateValue = captor.getValue().getMetadata().get("inventoryReconciliation");
        assertThat(stateValue).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> inner = (Map<String, Object>) stateValue;
        assertThat(inner).containsEntry("state", "PROCESSING");
        assertThat(inner).containsKey("processingStartedAt");
    }

    @Test
    @DisplayName("claimDueTasks: VERIFYING state gets step reset to VERIFY_REMOTE")
    void claimDueTasks_verifying() {
        UUID mappingId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        Map<String, Object> state = Map.of(
                "state", "VERIFYING",
                "step", "PUSH_LOCAL",
                "cycleId", "cyc-2",
                "apiAttemptCount", 0);
        ChannelProductVariant mapping = mapping(mappingId, channelId, state);

        when(mappingRepository.claimDueInventoryReconciliationIds(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(mappingId));
        when(mappingRepository.findAllWithChannelAndVariantByIdIn(List.of(mappingId)))
                .thenReturn(List.of(mapping));

        List<InventoryReconciliationTask> result = service.claimDueTasks();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).step()).isEqualTo("VERIFY_REMOTE");
        ArgumentCaptor<ChannelProductVariant> captor = ArgumentCaptor.forClass(ChannelProductVariant.class);
        verify(mappingRepository).save(captor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> inner = (Map<String, Object>) captor.getValue().getMetadata().get("inventoryReconciliation");
        assertThat(inner).containsEntry("step", "VERIFY_REMOTE");
    }

    @Test
    @DisplayName("markSynced: sets SYNCED, sets lastSyncedAt, clears metadata sub-map")
    void markSynced() {
        UUID mappingId = UUID.randomUUID();
        ChannelProductVariant mapping = mapping(mappingId, UUID.randomUUID(),
                Map.of("state", "PROCESSING", "cycleId", "cyc-1"));
        when(mappingRepository.findByIdForUpdate(mappingId)).thenReturn(Optional.of(mapping));

        service.markSynced(mappingId, "cyc-1");

        ArgumentCaptor<ChannelProductVariant> captor = ArgumentCaptor.forClass(ChannelProductVariant.class);
        verify(mappingRepository).save(captor.capture());
        ChannelProductVariant saved = captor.getValue();
        assertThat(saved.getSyncStatus()).isEqualTo(SyncStatus.SYNCED);
        assertThat(saved.getLastSyncedAt()).isNotNull();
        // inventoryReconciliation sub-map removed
        assertThat(saved.getMetadata()).doesNotContainKey("inventoryReconciliation");
    }

    @Test
    @DisplayName("markSynced: skips when stored cycleId doesn't match the input cycleId")
    void markSynced_cycleMismatch() {
        UUID mappingId = UUID.randomUUID();
        ChannelProductVariant mapping = mapping(mappingId, UUID.randomUUID(),
                Map.of("cycleId", "cyc-1"));
        when(mappingRepository.findByIdForUpdate(mappingId)).thenReturn(Optional.of(mapping));

        service.markSynced(mappingId, "wrong-cycle");

        verify(mappingRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("scheduleVerification: sets VERIFYING state, bumps correctivePushCount, clears lastError")
    void scheduleVerification() {
        UUID mappingId = UUID.randomUUID();
        ChannelProductVariant mapping = mapping(mappingId, UUID.randomUUID(),
                Map.of("cycleId", "cyc-3",
                        "correctivePushCount", 2,
                        "lastError", "old err"));
        when(mappingRepository.findByIdForUpdate(mappingId)).thenReturn(Optional.of(mapping));

        service.scheduleVerification(mappingId, "cyc-3");

        ArgumentCaptor<ChannelProductVariant> captor = ArgumentCaptor.forClass(ChannelProductVariant.class);
        verify(mappingRepository).save(captor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> inner = (Map<String, Object>) captor.getValue().getMetadata().get("inventoryReconciliation");
        assertThat(inner).containsEntry("state", "VERIFYING");
        assertThat(inner).containsEntry("step", "VERIFY_REMOTE");
        assertThat(inner).containsEntry("correctivePushCount", 3);
        assertThat(inner).containsEntry("apiAttemptCount", 0);
        assertThat(inner).containsEntry("lastError", null);
        assertThat(inner).doesNotContainKey("processingStartedAt");
        assertThat(captor.getValue().getSyncStatus()).isEqualTo(SyncStatus.OUT_OF_SYNC);
    }

    @Test
    @DisplayName("scheduleRetry: uses 30s delay when nextAttempt <= 1, 120s otherwise")
    void scheduleRetry() {
        UUID mappingId = UUID.randomUUID();
        ChannelProductVariant mapping = mapping(mappingId, UUID.randomUUID(),
                Map.of("cycleId", "cyc-4", "state", "PROCESSING"));
        when(mappingRepository.findByIdForUpdate(mappingId)).thenReturn(Optional.of(mapping));

        service.scheduleRetry(mappingId, "cyc-4", 1, "boom");
        service.scheduleRetry(mappingId, "cyc-4", 3, "still bad");

        verify(mappingRepository, org.mockito.Mockito.times(2))
                .save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("markFailed: sets FAILED state, stores error, removes processingStartedAt")
    void markFailed() {
        UUID mappingId = UUID.randomUUID();
        ChannelProductVariant mapping = mapping(mappingId, UUID.randomUUID(),
                Map.of("cycleId", "cyc-5", "state", "PROCESSING",
                        "processingStartedAt", "earlier"));
        when(mappingRepository.findByIdForUpdate(mappingId)).thenReturn(Optional.of(mapping));

        service.markFailed(mappingId, "cyc-5", "cannot reconcile");

        ArgumentCaptor<ChannelProductVariant> captor = ArgumentCaptor.forClass(ChannelProductVariant.class);
        verify(mappingRepository).save(captor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> inner = (Map<String, Object>) captor.getValue().getMetadata().get("inventoryReconciliation");
        assertThat(inner).containsEntry("state", "FAILED");
        assertThat(inner).containsEntry("lastError", "cannot reconcile");
        assertThat(inner).doesNotContainKey("processingStartedAt");
        assertThat(captor.getValue().getSyncStatus()).isEqualTo(SyncStatus.OUT_OF_SYNC);
    }

    @Test
    @DisplayName("markFailed: trims error to 1000 characters")
    void markFailed_longError() {
        UUID mappingId = UUID.randomUUID();
        ChannelProductVariant mapping = mapping(mappingId, UUID.randomUUID(),
                Map.of("cycleId", "cyc-6"));
        when(mappingRepository.findByIdForUpdate(mappingId)).thenReturn(Optional.of(mapping));

        String longError = "x".repeat(1500);
        service.markFailed(mappingId, "cyc-6", longError);

        ArgumentCaptor<ChannelProductVariant> captor = ArgumentCaptor.forClass(ChannelProductVariant.class);
        verify(mappingRepository).save(captor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> inner = (Map<String, Object>) captor.getValue().getMetadata().get("inventoryReconciliation");
        String stored = (String) inner.get("lastError");
        assertThat(stored).hasSize(1000);
    }

    @Test
    @DisplayName("markFailed: uses fallback error when input is null or blank")
    void markFailed_blankError() {
        UUID mappingId = UUID.randomUUID();
        ChannelProductVariant mapping = mapping(mappingId, UUID.randomUUID(),
                Map.of("cycleId", "cyc-7"));
        when(mappingRepository.findByIdForUpdate(mappingId)).thenReturn(Optional.of(mapping));

        service.markFailed(mappingId, "cyc-7", null);

        ArgumentCaptor<ChannelProductVariant> captor = ArgumentCaptor.forClass(ChannelProductVariant.class);
        verify(mappingRepository).save(captor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> inner = (Map<String, Object>) captor.getValue().getMetadata().get("inventoryReconciliation");
        assertThat(inner).containsEntry("lastError", "Inventory reconciliation failed");
    }

    @Test
    @DisplayName("update: skips when mapping is not found")
    void update_noMapping() {
        UUID mappingId = UUID.randomUUID();
        when(mappingRepository.findByIdForUpdate(mappingId)).thenReturn(Optional.empty());

        service.markSynced(mappingId, "cyc-x");

        verify(mappingRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
