package fu.osms.orderreturn.service.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.common.enums.PlatformType;
import fu.osms.orderreturn.model.OrderReturnSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderReturnPersistenceServiceImpl Tests")
class OrderReturnPersistenceServiceImplTest {

    @Mock private OrderReturnPersistenceTransactionService transactionService;

    private OrderReturnPersistenceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderReturnPersistenceServiceImpl(transactionService);
    }

    private Channel channel() {
        return Channel.builder().id(UUID.randomUUID()).platform(PlatformType.LAZADA).build();
    }

    private OrderReturnSnapshot snapshot() {
        return new OrderReturnSnapshot("EXT-RT-1", "EXT-OR-1", null, null,
                null, null, false, false, java.util.List.of(), null);
    }

    @Test
    @DisplayName("upsert: returns Optional from the transaction service on first attempt (no retry)")
    void upsert_noRetry() {
        Channel channel = channel();
        UUID returnId = UUID.randomUUID();
        OrderReturnSnapshot snap = snapshot();
        when(transactionService.upsertOnce(channel.getId(), snap))
                .thenReturn(Optional.of(returnId));

        Optional<UUID> result = service.upsert(channel, snap);

        assertThat(result).contains(returnId);
        verify(transactionService, times(1)).upsertOnce(channel.getId(), snap);
    }

    @Test
    @DisplayName("upsert: rethrows non-retryable exceptions immediately without retrying")
    void upsert_nonRetryable() {
        Channel channel = channel();
        OrderReturnSnapshot snap = snapshot();
        IllegalStateException ex = new IllegalStateException("boom");
        when(transactionService.upsertOnce(channel.getId(), snap)).thenThrow(ex);

        assertThatThrownBy(() -> service.upsert(channel, snap))
                .isInstanceOf(IllegalStateException.class);
        verify(transactionService, times(1)).upsertOnce(channel.getId(), snap);
    }

    @Test
    @DisplayName("upsert: retries CannotAcquireLockException up to MAX_ATTEMPTS, then rethrows")
    void upsert_retriesCannotAcquireLock() {
        Channel channel = channel();
        OrderReturnSnapshot snap = snapshot();
        CannotAcquireLockException ex = new CannotAcquireLockException("lock");
        when(transactionService.upsertOnce(channel.getId(), snap)).thenThrow(ex);

        assertThatThrownBy(() -> service.upsert(channel, snap))
                .isInstanceOf(CannotAcquireLockException.class);
        verify(transactionService, times(3)).upsertOnce(channel.getId(), snap);
    }

    @Test
    @DisplayName("upsert: retries DeadlockLoserDataAccessException")
    void upsert_retriesDeadlock() {
        Channel channel = channel();
        OrderReturnSnapshot snap = snapshot();
        DeadlockLoserDataAccessException ex = new DeadlockLoserDataAccessException("deadlock", null);
        when(transactionService.upsertOnce(channel.getId(), snap)).thenThrow(ex);

        assertThatThrownBy(() -> service.upsert(channel, snap))
                .isInstanceOf(DeadlockLoserDataAccessException.class);
        verify(transactionService, times(3)).upsertOnce(channel.getId(), snap);
    }

    @Test
    @DisplayName("upsert: retries PessimisticLockingFailureException")
    void upsert_retriesPessimistic() {
        Channel channel = channel();
        OrderReturnSnapshot snap = snapshot();
        PessimisticLockingFailureException ex = new PessimisticLockingFailureException("pess");
        when(transactionService.upsertOnce(channel.getId(), snap)).thenThrow(ex);

        assertThatThrownBy(() -> service.upsert(channel, snap))
                .isInstanceOf(PessimisticLockingFailureException.class);
        verify(transactionService, times(3)).upsertOnce(channel.getId(), snap);
    }

    @Test
    @DisplayName("upsert: retries ObjectOptimisticLockingFailureException")
    void upsert_retriesOptimistic() {
        Channel channel = channel();
        OrderReturnSnapshot snap = snapshot();
        ObjectOptimisticLockingFailureException ex =
                new ObjectOptimisticLockingFailureException(fu.osms.orderreturn.entity.OrderReturn.class, 1L);
        when(transactionService.upsertOnce(channel.getId(), snap)).thenThrow(ex);

        assertThatThrownBy(() -> service.upsert(channel, snap))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        verify(transactionService, times(3)).upsertOnce(channel.getId(), snap);
    }

    @Test
    @DisplayName("upsert: recovers when a later attempt succeeds")
    void upsert_recoversOnRetry() {
        Channel channel = channel();
        OrderReturnSnapshot snap = snapshot();
        UUID returnId = UUID.randomUUID();
        when(transactionService.upsertOnce(channel.getId(), snap))
                .thenThrow(new CannotAcquireLockException("lock"))
                .thenThrow(new DeadlockLoserDataAccessException("deadlock", null))
                .thenReturn(Optional.of(returnId));

        Optional<UUID> result = service.upsert(channel, snap);

        assertThat(result).contains(returnId);
        verify(transactionService, times(3)).upsertOnce(channel.getId(), snap);
    }

    @Test
    @DisplayName("upsert: forwards channelId to transactionService from the channel")
    void upsert_forwardsChannelId() {
        Channel channel = Channel.builder().id(UUID.randomUUID()).platform(PlatformType.SHOPIFY).build();
        OrderReturnSnapshot snap = snapshot();
        when(transactionService.upsertOnce(channel.getId(), snap))
                .thenReturn(Optional.of(UUID.randomUUID()));

        service.upsert(channel, snap);

        verify(transactionService).upsertOnce(channel.getId(), snap);
    }
}
