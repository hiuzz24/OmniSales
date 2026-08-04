package fu.osms.sync.service.impl;

import fu.osms.sync.service.SyncLogLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryAutoPushSyncLogServiceImpl Tests")
class InventoryAutoPushSyncLogServiceImplTest {

    @Mock private SyncLogLifecycleService lifecycle;

    private InventoryAutoPushSyncLogServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InventoryAutoPushSyncLogServiceImpl(lifecycle);
    }

    @Test
    @DisplayName("JOB_TYPE constant is the marketplace auto-push job identifier")
    void jobType() {
        org.assertj.core.api.Assertions.assertThat(InventoryAutoPushSyncLogServiceImpl.JOB_TYPE)
                .isEqualTo("MARKETPLACE_INVENTORY_AUTO_PUSH");
    }

    @Test
    @DisplayName("start: delegates to lifecycle.start with the AUTO_PUSH job type")
    void start_delegates() {
        UUID channelId = UUID.randomUUID();
        when(lifecycle.start(channelId, InventoryAutoPushSyncLogServiceImpl.JOB_TYPE, 5))
                .thenReturn(UUID.randomUUID());

        service.start(channelId, 5);

        verify(lifecycle).start(channelId, InventoryAutoPushSyncLogServiceImpl.JOB_TYPE, 5);
    }

    @Test
    @DisplayName("markSynced: delegates to lifecycle.markSynced")
    void markSynced_delegates() {
        UUID id = UUID.randomUUID();
        service.markSynced(id, 10);
        verify(lifecycle).markSynced(eq(id), eq(10));
    }

    @Test
    @DisplayName("markFailed: delegates to lifecycle.markFailed with error summary")
    void markFailed_delegates() {
        UUID id = UUID.randomUUID();
        service.markFailed(id, 3, "oops");
        verify(lifecycle).markFailed(eq(id), eq(3), eq("oops"));
    }
}
