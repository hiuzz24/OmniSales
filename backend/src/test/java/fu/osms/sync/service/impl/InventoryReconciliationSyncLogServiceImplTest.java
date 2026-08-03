package fu.osms.sync.service.impl;

import fu.osms.sync.service.SyncLogLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryReconciliationSyncLogServiceImpl Tests")
class InventoryReconciliationSyncLogServiceImplTest {

    @Mock private SyncLogLifecycleService lifecycle;

    private InventoryReconciliationSyncLogServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InventoryReconciliationSyncLogServiceImpl(lifecycle);
    }

    @Test
    @DisplayName("JOB_TYPE constant is the marketplace reconciliation job identifier")
    void jobType() {
        assertThat(InventoryReconciliationSyncLogServiceImpl.JOB_TYPE)
                .isEqualTo("MARKETPLACE_INVENTORY_RECONCILE");
    }

    @Test
    @DisplayName("start: delegates to lifecycle.start with the RECONCILE job type")
    void start_delegates() {
        UUID channelId = UUID.randomUUID();
        when(lifecycle.start(channelId, InventoryReconciliationSyncLogServiceImpl.JOB_TYPE, 7))
                .thenReturn(UUID.randomUUID());

        service.start(channelId, 7);

        verify(lifecycle).start(channelId, InventoryReconciliationSyncLogServiceImpl.JOB_TYPE, 7);
    }

    @Test
    @DisplayName("markSynced: delegates to lifecycle.markSynced")
    void markSynced_delegates() {
        UUID id = UUID.randomUUID();
        service.markSynced(id, 5);
        verify(lifecycle).markSynced(eq(id), eq(5));
    }

    @Test
    @DisplayName("markFailed: delegates to lifecycle.markFailed with error message")
    void markFailed_delegates() {
        UUID id = UUID.randomUUID();
        service.markFailed(id, 2, "diff");
        verify(lifecycle).markFailed(eq(id), eq(2), eq("diff"));
    }
}
