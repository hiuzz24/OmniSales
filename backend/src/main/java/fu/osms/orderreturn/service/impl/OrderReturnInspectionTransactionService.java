package fu.osms.orderreturn.service.impl;

import fu.osms.common.enums.PlatformType;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.orderreturn.dto.request.OrderReturnInspectionRequest;
import fu.osms.orderreturn.entity.OrderReturn;
import fu.osms.orderreturn.entity.OrderReturnItem;
import fu.osms.orderreturn.enums.OrderReturnStatus;
import fu.osms.orderreturn.repository.OrderReturnItemRepository;
import fu.osms.orderreturn.repository.OrderReturnRepository;
import fu.osms.sync.service.MarketplaceWarehouseConsistencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderReturnInspectionTransactionService {

    private final OrderReturnRepository returnRepository;
    private final OrderReturnItemRepository itemRepository;
    private final MarketplaceWarehouseConsistencyService warehouseConsistencyService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID saveInspection(UUID returnId, OrderReturnInspectionRequest request) {
        OrderReturn orderReturn = returnRepository.findForUpdateById(returnId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_RETURN_NOT_FOUND));
        if (orderReturn.getStatus() != OrderReturnStatus.AWAITING_RETURN
                && orderReturn.getStatus() != OrderReturnStatus.RETURN_IN_TRANSIT) {
            throw new AppException(ErrorCode.ORDER_RETURN_INVALID_STATE);
        }

        List<OrderReturnItem> items = itemRepository.findByReturnIdWithDetails(returnId);
        Map<UUID, OrderReturnItem> byId = new HashMap<>();
        items.forEach(item -> byId.put(item.getId(), item));
        if (request.items().size() != items.size()) {
            throw new AppException(ErrorCode.ORDER_RETURN_QUANTITY_INVALID,
                    "Inspection must include every return item");
        }

        for (OrderReturnInspectionRequest.Item value : request.items()) {
            OrderReturnItem item = byId.get(value.returnItemId());
            if (item == null) {
                throw new AppException(ErrorCode.ORDER_RETURN_QUANTITY_INVALID,
                        "Sản phẩm trả hàng đã được cập nhật. Vui lòng tải lại trang trước khi kiểm hàng.");
            }
            validateQuantity(item, value);
            item.setReceivedQuantity(value.receivedQuantity());
            item.setRestockableQuantity(value.restockableQuantity());
            item.setDamagedQuantity(value.damagedQuantity());
            item.setMissingQuantity(value.missingQuantity());
        }

        Warehouse warehouse = warehouseConsistencyService.resolveMasterWarehouse();
        boolean partialRequiresManual = (orderReturn.getPlatform() == PlatformType.SHOPIFY
                || orderReturn.getPlatform() == PlatformType.TIKTOK)
                && items.stream().anyMatch(item -> item.getMissingQuantity() != null
                        && item.getMissingQuantity() > 0);
        orderReturn.setWarehouse(warehouse);
        orderReturn.setInspectedAt(OffsetDateTime.now());
        orderReturn.setStatus(OrderReturnStatus.INSPECTED);
        orderReturn.setLastSyncError(partialRequiresManual
                ? "Đơn trả hàng bị thiếu sản phẩm. Vui lòng xử lý thủ công trên sàn; hàng thiếu sẽ không được nhập kho."
                : null);
        itemRepository.saveAll(items);
        return returnRepository.save(orderReturn).getId();
    }

    private void validateQuantity(OrderReturnItem item, OrderReturnInspectionRequest.Item value) {
        if (value.receivedQuantity() != value.restockableQuantity() + value.damagedQuantity()
                || value.receivedQuantity() + value.missingQuantity() != item.getApprovedQuantity()) {
            throw new AppException(ErrorCode.ORDER_RETURN_QUANTITY_INVALID,
                    "Số lượng không hợp lệ cho SKU " + item.getSnapshotSku()
                            + ": đã nhận phải bằng hàng đạt + hàng hỏng, và đã nhận + hàng thiếu phải bằng "
                            + item.getApprovedQuantity() + ".");
        }
    }
}
