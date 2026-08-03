package fu.osms.sync.inventory.impl;

import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.enums.SyncStatus;
import fu.osms.sync.inventory.InventoryObservation;
import fu.osms.sync.inventory.InventoryReconciliationProperties;
import fu.osms.sync.service.MarketplaceStockQuantityResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryReconciliationServiceImpl Tests")
class InventoryReconciliationServiceImplTest {

    @Mock private ChannelProductVariantRepository mappingRepository;
    @Mock private MarketplaceStockQuantityResolver quantityResolver;

    private InventoryReconciliationServiceImpl service;
    private final InventoryReconciliationProperties properties = new InventoryReconciliationProperties();

    @BeforeEach
    void setUp() {
        service = new InventoryReconciliationServiceImpl(
                mappingRepository, quantityResolver, properties);
    }

    /**
     * Build a mapping whose metadata has the nested inventoryReconciliation sub-map populated.
     */
    private ChannelProductVariant mapping(UUID id, Map<String, Object> innerState) {
        Map<String, Object> meta = new HashMap<>();
        if (innerState != null) {
            meta.put("inventoryReconciliation", new HashMap<>(innerState));
        }
        return ChannelProductVariant.builder().id(id).metadata(meta).build();
    }

    private InventoryObservation observation(UUID mappingId, int remoteAvailable) {
        return new InventoryObservation(mappingId, PlatformType.SHOPIFY, remoteAvailable,
                OffsetDateTime.now(), "ext-loc", UUID.randomUUID());
    }

    @Test
    @DisplayName("observe: no-op when mapping is not found")
    void mappingNotFound() {
        UUID mappingId = UUID.randomUUID();
        when(mappingRepository.findByIdForUpdate(mappingId)).thenReturn(Optional.empty());

        service.observe(observation(mappingId, 5));

        verify(mappingRepository, never()).save(any());
    }

    @Test
    @DisplayName("observe: marks SYNCED when local available equals remote available")
    void synced() {
        UUID mappingId = UUID.randomUUID();
        ChannelProductVariant mapping = mapping(mappingId, null);
        when(mappingRepository.findByIdForUpdate(mappingId)).thenReturn(Optional.of(mapping));
        when(quantityResolver.resolveAvailableByMappingIds(eq(List.of(mappingId))))
                .thenReturn(Map.of(mappingId, 5));

        service.observe(observation(mappingId, 5));

        ArgumentCaptor<ChannelProductVariant> captor = ArgumentCaptor.forClass(ChannelProductVariant.class);
        verify(mappingRepository).save(captor.capture());
        assertThat(captor.getValue().getSyncStatus()).isEqualTo(SyncStatus.SYNCED);
        assertThat(captor.getValue().getLastSyncedAt()).isNotNull();
        // inventoryReconciliation sub-map cleared
        assertThat(captor.getValue().getMetadata()).doesNotContainKey("inventoryReconciliation");
    }

    @Test
    @DisplayName("observe: no-op when observation.observedAt is before the previously stored observedAt")
    void staleObservation() {
        UUID mappingId = UUID.randomUUID();
        OffsetDateTime prev = OffsetDateTime.now();
        ChannelProductVariant mapping = mapping(mappingId,
                Map.of("observedAt", prev.toString()));
        when(mappingRepository.findByIdForUpdate(mappingId)).thenReturn(Optional.of(mapping));

        InventoryObservation older = new InventoryObservation(mappingId, PlatformType.SHOPIFY, 1,
                prev.minusMinutes(1), null, UUID.randomUUID());
        service.observe(older);

        verify(mappingRepository, never()).save(any());
    }

    @Test
    @DisplayName("observe: when previous state is FAILED, refresh remote fields without resetting the cycle")
    void failedStateRefresh() {
        UUID mappingId = UUID.randomUUID();
        ChannelProductVariant mapping = mapping(mappingId,
                Map.of("state", "FAILED"));
        when(mappingRepository.findByIdForUpdate(mappingId)).thenReturn(Optional.of(mapping));
        when(quantityResolver.resolveAvailableByMappingIds(eq(List.of(mappingId))))
                .thenReturn(Map.of(mappingId, 0));

        service.observe(observation(mappingId, 7));

        ArgumentCaptor<ChannelProductVariant> captor = ArgumentCaptor.forClass(ChannelProductVariant.class);
        verify(mappingRepository).save(captor.capture());
        ChannelProductVariant saved = captor.getValue();
        assertThat(saved.getSyncStatus()).isEqualTo(SyncStatus.OUT_OF_SYNC);
        @SuppressWarnings("unchecked")
        Map<String, Object> inner = (Map<String, Object>) saved.getMetadata().get("inventoryReconciliation");
        assertThat(inner).containsEntry("remoteAvailable", 7);
        assertThat(inner).containsEntry("state", "FAILED");
    }

    @Test
    @DisplayName("observe: starts a new PENDING cycle when mapping was previously SYNCED")
    void newPendingCycle() {
        UUID mappingId = UUID.randomUUID();
        ChannelProductVariant mapping = mapping(mappingId, null); // no inventoryReconciliation sub-map
        when(mappingRepository.findByIdForUpdate(mappingId)).thenReturn(Optional.of(mapping));
        when(quantityResolver.resolveAvailableByMappingIds(eq(List.of(mappingId))))
                .thenReturn(Map.of(mappingId, 0));

        service.observe(observation(mappingId, 3));

        ArgumentCaptor<ChannelProductVariant> captor = ArgumentCaptor.forClass(ChannelProductVariant.class);
        verify(mappingRepository).save(captor.capture());
        ChannelProductVariant saved = captor.getValue();
        assertThat(saved.getSyncStatus()).isEqualTo(SyncStatus.OUT_OF_SYNC);
        @SuppressWarnings("unchecked")
        Map<String, Object> inner = (Map<String, Object>) saved.getMetadata().get("inventoryReconciliation");
        assertThat(inner).containsEntry("state", "PENDING");
        assertThat(inner).containsEntry("step", "READ_REMOTE");
        assertThat(inner).containsEntry("remoteAvailable", 3);
        assertThat(inner).containsKey("reconcileAfter");
        assertThat(inner).containsKey("cycleId");
        assertThat(inner.get("cycleId")).isNotNull();
    }

    @Test
    @DisplayName("observe: keeps the active cycle (does not reset step) when previous state is PENDING")
    void activeCycleKeepsStep() {
        UUID mappingId = UUID.randomUUID();
        ChannelProductVariant mapping = mapping(mappingId,
                Map.of("state", "PENDING", "step", "PUSH_LOCAL"));
        when(mappingRepository.findByIdForUpdate(mappingId)).thenReturn(Optional.of(mapping));
        when(quantityResolver.resolveAvailableByMappingIds(eq(List.of(mappingId))))
                .thenReturn(Map.of(mappingId, 0));

        service.observe(observation(mappingId, 9));

        ArgumentCaptor<ChannelProductVariant> captor = ArgumentCaptor.forClass(ChannelProductVariant.class);
        verify(mappingRepository).save(captor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> inner = (Map<String, Object>) captor.getValue().getMetadata().get("inventoryReconciliation");
        assertThat(inner).containsEntry("state", "PENDING");
        assertThat(inner).containsEntry("step", "PUSH_LOCAL"); // unchanged
        assertThat(inner).containsEntry("remoteAvailable", 9);
    }
}