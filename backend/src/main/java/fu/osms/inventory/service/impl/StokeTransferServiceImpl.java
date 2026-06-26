package fu.osms.inventory.service.impl;


import fu.osms.auth.repository.UserRepository;
import fu.osms.auth.repository.UserRoleRepository;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.inventory.dto.request.StockTransferItemRequest;
import fu.osms.inventory.dto.request.StockTransferRequest;
import fu.osms.inventory.dto.response.StockTransferResponseDTO;
import fu.osms.inventory.dto.response.StockTransferResponse;
import fu.osms.inventory.dto.response.TransferInventoryListResponse;
import fu.osms.inventory.dto.response.TransferSummaryDTO;
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.inventory.entity.InventoryTransaction;
import fu.osms.inventory.entity.StockTransfer;
import fu.osms.inventory.entity.StockTransferItem;
import fu.osms.inventory.enums.InvTxnType;
import fu.osms.inventory.mapper.StockTransferMapper;
import fu.osms.inventory.repository.*;
import fu.osms.inventory.service.StokeTransferService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class StokeTransferServiceImpl implements StokeTransferService {

    private final StockTransferRepository transferRepository;
    private final StockTransferMapper transferMapper;
    private final WarehouseRepository warehouseRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final ProductVariantRepository variantRepository;
    private final StokeTransferItemRepository stokeTransferItemRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final fu.osms.notification.service.NotificationService notificationService;

    @Override
    public TransferInventoryListResponse getTransferListData(String status, UUID warehouseId, String keyword, int page, int size) {
        TransferSummaryDTO summary = transferRepository.getTransferSummary();
        // Tránh trường hợp bảng trống trả về null cho các hàm SUM
        if (summary == null) {
            summary = new TransferSummaryDTO(0, 0, 0, 0);
        }

        // 2. Lấy danh sách phân trang, sắp xếp theo ngày tạo mới nhất
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<StockTransfer> transferPage = transferRepository.searchTransfers(status, warehouseId, keyword, pageable);

        // 3. Map Entity sang DTO hiển thị bảng
        var listData = transferPage.getContent().stream().map(st -> {
            StockTransferResponseDTO dto = new StockTransferResponseDTO();
            dto.setId(st.getId());
            dto.setTransferCode(st.getTransferCode()); // Giả định bạn có code riêng hoặc dùng ID làm code
            dto.setFromWarehouseName(st.getFromWarehouse() != null ? st.getFromWarehouse().getName() : "");
            dto.setToWarehouseName(st.getToWarehouse() != null ? st.getToWarehouse().getName() : "");
            dto.setStatus(st.getStatus());
            dto.setCreatedAt(st.getCreatedAt());
            dto.setCreatedBy(st.getCreatedBy() != null ? st.getCreatedBy().getFullName() : ""); // Tránh NullPointerException
            dto.setNote(st.getNote());

            // Calculate item counts safely
            int skuCount = st.getItems() != null ? st.getItems().size() : 0;
            int totalQuantity = st.getItems() != null ? st.getItems().stream().mapToInt(StockTransferItem::getQuantity).sum() : 0;
            dto.setSkuCount(skuCount);
            dto.setTotalQuantity(totalQuantity);
            dto.setToWarehouseId(st.getToWarehouse() != null ? st.getToWarehouse().getId() : null);
            dto.setCreatedById(st.getCreatedBy() != null ? st.getCreatedBy().getId() : null);

            return dto;
        }).collect(Collectors.toList());

        // 4. Gom dữ liệu trả về
        return new TransferInventoryListResponse(
                summary,
                listData,
                transferPage.getTotalElements(),
                transferPage.getTotalPages()
        );
    }

    public String generateTransferCode() {
        String dateStr = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("ddMMyyyy"));
        String prefix = "Trans-" + dateStr + "-";

        return transferRepository.findLatestTransferCodeByPrefix(prefix + "%")
                .map(latestCode -> {
                    String lastIndexStr =
                            latestCode.substring(latestCode.lastIndexOf("-") + 1);

                    int nextIndex = Integer.parseInt(lastIndexStr) + 1;

                    return prefix + String.format("%04d", nextIndex);
                })
                .orElse(prefix + "0001");
    }

    @Override
    public void createTransferInventory(StockTransferRequest request) {

        System.out.println("request: " + request);

        if (request.getFromWarehouseId().equals(request.getToWarehouseId())) {
            throw new IllegalArgumentException("Kho xuất và kho nhận phải khác nhau.");
        }
        StockTransfer transfer = transferMapper.toEntity(request);
        transfer.setTransferCode(request.getTransferCode());
        
        String status = request.getStatus();
        if (status == null || status.isBlank()) {
            status = "IN_TRANSIT";
        }
        transfer.setStatus(status);
        
        transfer.setFromWarehouse(warehouseRepository.getReferenceById(request.getFromWarehouseId()));
        transfer.setToWarehouse(warehouseRepository.getReferenceById(request.getToWarehouseId()));
        transfer.setCreatedBy(userRepository.findUserById(request.getCreatedById()));
        transfer.setCreatedAt(request.getTransferTime() != null ? request.getTransferTime() : OffsetDateTime.now());
        transfer.setNote(request.getNote());
        transferRepository.save(transfer);

        boolean applyToSource = !"DRAFT".equalsIgnoreCase(status);

        for (StockTransferItemRequest itemReq : request.getItems()) {
            StockTransferItem itemEntity = transferMapper.toItemEntity(itemReq);
            itemEntity.setTransfer(transfer);
            itemEntity.setVariant(variantRepository.getReferenceById(itemReq.getVariantId()));
            stokeTransferItemRepository.save(itemEntity);

            if (applyToSource) {
                InventoryItem sourceInventory = inventoryItemRepository
                        .findByWarehouseIdAndVariantId(request.getFromWarehouseId(), itemReq.getVariantId())
                        .orElseThrow(() -> new IllegalArgumentException("Sản phẩm không tồn tại ở kho xuất."));

                if (sourceInventory.getAvailableQuantity() < itemReq.getQuantity()) {
                    throw new IllegalArgumentException("Số lượng chuyển kho vượt quá số lượng tồn khả dụng ở kho xuất.");
                }

                int sourceQtyBefore = sourceInventory.getQuantityOnHand();
                sourceInventory.setQuantityOnHand(sourceInventory.getQuantityOnHand() - itemReq.getQuantity());
                sourceInventory.setAvailableQuantity(sourceInventory.getAvailableQuantity() - itemReq.getQuantity());
                inventoryItemRepository.save(sourceInventory);

                InventoryTransaction sourceLog = new InventoryTransaction();
                sourceLog.setWarehouse(warehouseRepository.findById(request.getFromWarehouseId()).orElse(null));
                sourceLog.setVariant(variantRepository.findById(itemReq.getVariantId()).orElse(null));
                sourceLog.setType(InvTxnType.TRANSFER_OUT);
                sourceLog.setQuantityChange(-itemReq.getQuantity());
                sourceLog.setQuantityBefore(sourceQtyBefore);
                sourceLog.setQuantityAfter(sourceInventory.getQuantityOnHand());
                sourceLog.setReferenceType("TRANSFER");
                sourceLog.setReferenceId(transfer.getId());
                sourceLog.setPerformedBy(transfer.getCreatedBy());
                sourceLog.setPerformedAt(transfer.getCreatedAt());
                sourceLog.setNote(transfer.getNote());
                transactionRepository.save(sourceLog);
            }
        }

        if ("IN_TRANSIT".equalsIgnoreCase(status)) {
            sendNotificationsForTransfer(transfer);
        }
    }

    private void sendNotificationsForTransfer(StockTransfer transfer) {
        if (transfer.getToWarehouse() != null) {
            java.util.List<fu.osms.auth.entity.User> receivingStaff = userRepository.findByWarehouseId(transfer.getToWarehouse().getId());
            if (receivingStaff != null) {
                for (fu.osms.auth.entity.User staff : receivingStaff) {
                    try {
                        String fromWhName = transfer.getFromWarehouse() != null ? transfer.getFromWarehouse().getName() : "Kho xuất";
                        notificationService.createNotification(
                            staff.getId(),
                            "STOCK_TRANSFER",
                            "Yêu cầu chuyển kho mới",
                            "Kho " + fromWhName + " đã chuyển đơn hàng " + transfer.getTransferCode() + " đến cho bạn, xem chi tiết",
                            "INVENTORY",
                            transfer.getId()
                        );
                    } catch (Exception e) {
                        System.err.println("Lỗi gửi thông báo cho nhân viên " + staff.getId() + ": " + e.getMessage());
                    }
                }
            }
        }
    }

    @Override
    public StockTransferResponse getTransferDetail(UUID id) {
        StockTransfer transfer = transferRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu chuyển kho."));
        StockTransferResponse response = transferMapper.toResponse(transfer);
        if (transfer.getItems() != null) {
            response.setItems(transfer.getItems().stream()
                    .map(transferMapper::toItemResponse)
                    .collect(Collectors.toList()));
        }
        return response;
    }

    @Override
    public void updateStatus(UUID transferId, String newStatus, UUID userId) {
        StockTransfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu chuyển kho."));

        String oldStatus = transfer.getStatus();
        if (oldStatus.equalsIgnoreCase(newStatus)) {
            return;
        }

        if ("RECEIVED".equalsIgnoreCase(oldStatus) || "CANCELLED".equalsIgnoreCase(oldStatus)) {
            throw new IllegalArgumentException("Không thể cập nhật trạng thái cho phiếu chuyển kho đã hoàn thành hoặc đã hủy.");
        }

        fu.osms.auth.entity.User user = userRepository.findUserById(userId);

        // Kiểm tra quyền chuyển trạng thái đơn hàng
        var userRoles = userRoleRepository.findByUserId(user.getId());
        String roleName = userRoles.isEmpty() ? "" : userRoles.get(0).getRole().getName();
        
        boolean isGlobalManager = "OWNER".equalsIgnoreCase(roleName) || 
                                  "SYSTEM_ADMIN".equalsIgnoreCase(roleName);

        if ("RECEIVED".equalsIgnoreCase(newStatus) || "CANCELLED".equalsIgnoreCase(newStatus)) {
            // Khi đơn hàng ở trạng thái vận chuyển, chỉ có nhân viên ở kho nhận hoặc quản lý mới có quyền nhận hàng hoặc hủy phiếu
            boolean isDestStaff = user.getWarehouse() != null && 
                                  user.getWarehouse().getId().equals(transfer.getToWarehouse().getId());
                                  
            if (!isGlobalManager && !isDestStaff) {
                String actionName = "RECEIVED".equalsIgnoreCase(newStatus) ? "xác nhận hoàn thành" : "hủy";
                throw new IllegalArgumentException("Chỉ có nhân viên ở kho nhận mới có quyền " + actionName + " phiếu chuyển kho này.");
            }
        } else if ("IN_TRANSIT".equalsIgnoreCase(newStatus)) {
            // Chỉ nhân viên kho xuất, người tạo hoặc quản lý mới được quyền vận chuyển phiếu
            boolean isCreator = transfer.getCreatedBy() != null && user.getId().equals(transfer.getCreatedBy().getId());
            boolean isSourceStaff = user.getWarehouse() != null && 
                                    user.getWarehouse().getId().equals(transfer.getFromWarehouse().getId());
                                    
            if (!isGlobalManager && !isCreator && !isSourceStaff) {
                throw new IllegalArgumentException("Chỉ có nhân viên kho xuất hoặc người tạo mới có quyền bắt đầu vận chuyển phiếu này.");
            }
        }

        if ("IN_TRANSIT".equalsIgnoreCase(newStatus)) {
            if (!"DRAFT".equalsIgnoreCase(oldStatus)) {
                throw new IllegalArgumentException("Chỉ có thể vận chuyển phiếu đang ở trạng thái Nháp.");
            }

            for (StockTransferItem item : transfer.getItems()) {
                InventoryItem sourceInventory = inventoryItemRepository
                        .findByWarehouseIdAndVariantId(transfer.getFromWarehouse().getId(), item.getVariant().getId())
                        .orElseThrow(() -> new IllegalArgumentException("Sản phẩm không tồn tại ở kho xuất."));

                if (sourceInventory.getAvailableQuantity() < item.getQuantity()) {
                    throw new IllegalArgumentException("Số lượng chuyển kho vượt quá số lượng tồn khả dụng ở kho xuất cho sản phẩm: " + item.getVariant().getSku());
                }

                int sourceQtyBefore = sourceInventory.getQuantityOnHand();
                sourceInventory.setQuantityOnHand(sourceInventory.getQuantityOnHand() - item.getQuantity());
                sourceInventory.setAvailableQuantity(sourceInventory.getAvailableQuantity() - item.getQuantity());
                inventoryItemRepository.save(sourceInventory);

                InventoryTransaction sourceLog = new InventoryTransaction();
                sourceLog.setWarehouse(transfer.getFromWarehouse());
                sourceLog.setVariant(item.getVariant());
                sourceLog.setType(InvTxnType.TRANSFER_OUT);
                sourceLog.setQuantityChange(-item.getQuantity());
                sourceLog.setQuantityBefore(sourceQtyBefore);
                sourceLog.setQuantityAfter(sourceInventory.getQuantityOnHand());
                sourceLog.setReferenceType("TRANSFER");
                sourceLog.setReferenceId(transfer.getId());
                sourceLog.setPerformedBy(user);
                sourceLog.setPerformedAt(OffsetDateTime.now());
                sourceLog.setNote(transfer.getNote());
                transactionRepository.save(sourceLog);
            }

            transfer.setStatus("IN_TRANSIT");
            transferRepository.save(transfer);
            sendNotificationsForTransfer(transfer);

        } else if ("RECEIVED".equalsIgnoreCase(newStatus)) {
            if (!"IN_TRANSIT".equalsIgnoreCase(oldStatus)) {
                throw new IllegalArgumentException("Chỉ có thể nhận hàng cho phiếu chuyển kho đang vận chuyển.");
            }

            for (StockTransferItem item : transfer.getItems()) {
                InventoryItem destInventory = inventoryItemRepository
                        .findByWarehouseIdAndVariantId(transfer.getToWarehouse().getId(), item.getVariant().getId())
                        .orElseGet(() -> {
                            InventoryItem newItem = new InventoryItem();
                            newItem.setWarehouse(transfer.getToWarehouse());
                            newItem.setVariant(item.getVariant());
                            newItem.setQuantityOnHand(0);
                            newItem.setAvailableQuantity(0);
                            return newItem;
                        });

                int destQtyBefore = destInventory.getQuantityOnHand();
                destInventory.setQuantityOnHand(destInventory.getQuantityOnHand() + item.getQuantity());
                destInventory.setAvailableQuantity(destInventory.getAvailableQuantity() + item.getQuantity());
                inventoryItemRepository.save(destInventory);

                InventoryTransaction destLog = new InventoryTransaction();
                destLog.setWarehouse(transfer.getToWarehouse());
                destLog.setVariant(item.getVariant());
                destLog.setType(InvTxnType.TRANSFER_IN);
                destLog.setQuantityChange(item.getQuantity());
                destLog.setQuantityBefore(destQtyBefore);
                destLog.setQuantityAfter(destInventory.getQuantityOnHand());
                destLog.setReferenceType("TRANSFER");
                destLog.setReferenceId(transfer.getId());
                destLog.setPerformedBy(user);
                destLog.setPerformedAt(OffsetDateTime.now());
                transactionRepository.save(destLog);
            }

            transfer.setStatus("RECEIVED");
            transfer.setApprovedBy(user);
            transfer.setTransferTime(OffsetDateTime.now());
            transferRepository.save(transfer);

        } else if ("CANCELLED".equalsIgnoreCase(newStatus)) {
            if ("IN_TRANSIT".equalsIgnoreCase(oldStatus)) {
                for (StockTransferItem item : transfer.getItems()) {
                    InventoryItem sourceInventory = inventoryItemRepository
                            .findByWarehouseIdAndVariantId(transfer.getFromWarehouse().getId(), item.getVariant().getId())
                            .orElseGet(() -> {
                                InventoryItem newItem = new InventoryItem();
                                newItem.setWarehouse(transfer.getFromWarehouse());
                                newItem.setVariant(item.getVariant());
                                newItem.setQuantityOnHand(0);
                                newItem.setAvailableQuantity(0);
                                return newItem;
                            });

                    int sourceQtyBefore = sourceInventory.getQuantityOnHand();
                    sourceInventory.setQuantityOnHand(sourceInventory.getQuantityOnHand() + item.getQuantity());
                    sourceInventory.setAvailableQuantity(sourceInventory.getAvailableQuantity() + item.getQuantity());
                    inventoryItemRepository.save(sourceInventory);

                    InventoryTransaction sourceLog = new InventoryTransaction();
                    sourceLog.setWarehouse(transfer.getFromWarehouse());
                    sourceLog.setVariant(item.getVariant());
                    sourceLog.setType(InvTxnType.TRANSFER_IN);
                    sourceLog.setQuantityChange(item.getQuantity());
                    sourceLog.setQuantityBefore(sourceQtyBefore);
                    sourceLog.setQuantityAfter(sourceInventory.getQuantityOnHand());
                    sourceLog.setReferenceType("TRANSFER");
                    sourceLog.setReferenceId(transfer.getId());
                    sourceLog.setPerformedBy(user);
                    sourceLog.setPerformedAt(OffsetDateTime.now());
                    sourceLog.setNote("Hoàn trả do hủy phiếu chuyển");
                    transactionRepository.save(sourceLog);
                }
            }

            transfer.setStatus("CANCELLED");
            transfer.setApprovedBy(user);
            transferRepository.save(transfer);
        } else {
            throw new IllegalArgumentException("Trạng thái mới không hợp lệ.");
        }
    }
}