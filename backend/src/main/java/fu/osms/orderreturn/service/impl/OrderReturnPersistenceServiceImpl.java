package fu.osms.orderreturn.service.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.orderreturn.model.OrderReturnSnapshot;
import fu.osms.orderreturn.service.OrderReturnPersistenceService;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderReturnPersistenceServiceImpl implements OrderReturnPersistenceService {

    private static final int MAX_ATTEMPTS = 3;

    private final OrderReturnPersistenceTransactionService transactionService;

    /** Chỉ thử lại xung đột lock/version nội bộ, không bao giờ gọi lại API platform. */
    @Override
    public Optional<UUID> upsert(Channel channel, OrderReturnSnapshot snapshot) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return transactionService.upsertOnce(channel.getId(), snapshot);
            } catch (RuntimeException exception) {
                if (!isRetryable(exception)) {
                    throw exception;
                }
                lastFailure = exception;
                log.warn("[OrderReturnPersistence] Concurrent DB write channelId={} externalReturnId={} attempt={}/{}",
                        channel.getId(), snapshot.externalReturnId(), attempt, MAX_ATTEMPTS, exception);
            }
        }
        throw lastFailure == null
                ? new IllegalStateException("Order return persistence failed without a cause")
                : lastFailure;
    }

    private boolean isRetryable(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ObjectOptimisticLockingFailureException
                    || current instanceof OptimisticLockException
                    || current instanceof CannotAcquireLockException
                    || current instanceof PessimisticLockingFailureException
                    || current instanceof DeadlockLoserDataAccessException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
