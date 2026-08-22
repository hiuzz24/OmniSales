package fu.osms.order.exception;

import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;

public class OrderMovedToWaitingStockException extends AppException {
    public OrderMovedToWaitingStockException() {
        super(ErrorCode.ORDER_MOVED_TO_WAITING_STOCK);
    }
}
