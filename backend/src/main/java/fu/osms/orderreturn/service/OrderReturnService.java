package fu.osms.orderreturn.service;

import fu.osms.common.dto.PageResponse;
import fu.osms.orderreturn.dto.request.OrderReturnInspectionRequest;
import fu.osms.orderreturn.dto.request.OrderReturnRejectRequest;
import fu.osms.orderreturn.dto.response.OrderReturnRejectOptionsResponse;
import fu.osms.orderreturn.dto.response.OrderReturnResponse;

import java.util.UUID;

public interface OrderReturnService {
    PageResponse<OrderReturnResponse> getAll(int page, int size);

    OrderReturnResponse getById(UUID id);

    OrderReturnResponse approve(UUID id);

    OrderReturnRejectOptionsResponse getRejectOptions(UUID id);

    OrderReturnResponse reject(UUID id, OrderReturnRejectRequest request);

    OrderReturnResponse inspect(UUID id, OrderReturnInspectionRequest request);

    OrderReturnResponse refresh(UUID id);

    OrderReturnResponse checkAction(UUID id);

    OrderReturnResponse retryAction(UUID id);

    OrderReturnResponse retryStock(UUID id);
}
