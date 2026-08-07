package fu.osms.orderreturn.service.impl;

import fu.osms.orderreturn.entity.OrderReturn;
import fu.osms.orderreturn.enums.OrderReturnStatus;

import java.time.OffsetDateTime;

final class OrderReturnTransitionPolicy {

    void apply(OrderReturn target, OrderReturnStatus incoming) {
        if (incoming == null || incoming == target.getStatus() || isTerminal(target.getStatus())) return;
        if (rank(incoming) >= rank(target.getStatus()) || incoming == OrderReturnStatus.REJECTED) {
            target.setStatus(incoming);
            if (incoming == OrderReturnStatus.AWAITING_RETURN && target.getApprovedAt() == null) {
                target.setApprovedAt(OffsetDateTime.now());
            }
        }
    }

    private boolean isTerminal(OrderReturnStatus status) {
        return status == OrderReturnStatus.REJECTED || status == OrderReturnStatus.COMPLETED;
    }

    private int rank(OrderReturnStatus status) {
        if (status == null) return 0;
        return switch (status) {
            case PENDING_APPROVAL -> 1;
            case AWAITING_RETURN -> 2;
            case RETURN_IN_TRANSIT -> 3;
            case INSPECTED -> 4;
            case PLATFORM_PROCESSING -> 5;
            case PENDING_STOCK -> 6;
            case COMPLETED -> 7;
            case REJECTED, FAILED -> 8;
        };
    }
}
