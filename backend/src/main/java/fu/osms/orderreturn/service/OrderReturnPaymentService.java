package fu.osms.orderreturn.service;

import java.util.UUID;

public interface OrderReturnPaymentService {
    void projectPayment(UUID returnId);
}
