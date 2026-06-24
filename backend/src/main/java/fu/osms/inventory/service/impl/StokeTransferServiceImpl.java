package fu.osms.inventory.service.impl;


import fu.osms.auth.repository.UserRepository;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.inventory.dto.request.StockTransferItemRequest;
import fu.osms.inventory.dto.request.StockTransferRequest;
import fu.osms.inventory.dto.response.StockTransferResponseDTO;
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
            throw new IllegalArgumentException("Source and destination warehouses cannot be the same.");
        }
        StockTransfer transfer = transferMapper.toEntity(request);
        transfer.setTransferCode(request.getTransferCode());
        transfer.setStatus("RECEIVED");
        transfer.setFromWarehouse(warehouseRepository.getReferenceById(request.getFromWarehouseId()));
        transfer.setToWarehouse(warehouseRepository.getReferenceById(request.getToWarehouseId()));
        transfer.setCreatedBy(userRepository.findUserById(request.getCreatedById()));
        transfer.setCreatedAt(request.getTransferTime());
        transfer.setNote(request.getNote());
        transferRepository.save(transfer);

        for (StockTransferItemRequest itemReq : request.getItems()) {
            StockTransferItem itemEntity = transferMapper.toItemEntity(itemReq);
            itemEntity.setTransfer(transfer);
            itemEntity.setVariant(variantRepository.getReferenceById(itemReq.getVariantId()));
            stokeTransferItemRepository.save(itemEntity);
            InventoryItem sourceInventory = inventoryItemRepository
                    .findByWarehouseIdAndVariantId(request.getFromWarehouseId(), itemReq.getVariantId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found in source warehouse."));

            if (sourceInventory.getAvailableQuantity() < itemReq.getQuantity()) {
                throw new IllegalArgumentException("Transfer quantity exceeds available inventory.");
            }

            int sourceQtyBefore = sourceInventory.getQuantityOnHand();
            sourceInventory.setQuantityOnHand(sourceInventory.getQuantityOnHand() - itemReq.getQuantity());
            sourceInventory.setAvailableQuantity(sourceInventory.getAvailableQuantity() - itemReq.getQuantity());
            inventoryItemRepository.save(sourceInventory);

            InventoryItem destInventory = inventoryItemRepository
                    .findByWarehouseIdAndVariantId(request.getToWarehouseId(), itemReq.getVariantId())
                    .orElseGet(() -> {
                        InventoryItem newItem = new InventoryItem();
                        newItem.setWarehouse(warehouseRepository.findById(request.getToWarehouseId()).orElse(null));
                        newItem.setVariant(variantRepository.findById(itemReq.getVariantId()).orElse(null));
                        newItem.setQuantityOnHand(0);
                        newItem.setAvailableQuantity(0);
                        return newItem;
                    });

            int destQtyBefore = destInventory.getQuantityOnHand();
            destInventory.setQuantityOnHand(destInventory.getQuantityOnHand() + itemReq.getQuantity());
            destInventory.setAvailableQuantity(destInventory.getAvailableQuantity() + itemReq.getQuantity());
            inventoryItemRepository.save(destInventory);


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

            InventoryTransaction destLog = new InventoryTransaction();
            destLog.setWarehouse(warehouseRepository.findById(request.getToWarehouseId()).orElse(null));
            destLog.setVariant(variantRepository.findById(itemReq.getVariantId()).orElse(null));
            destLog.setType(InvTxnType.TRANSFER_IN);
            destLog.setQuantityChange(itemReq.getQuantity());
            destLog.setQuantityBefore(destQtyBefore);
            destLog.setQuantityAfter(destInventory.getQuantityOnHand());
            destLog.setReferenceType("TRANSFER");
            destLog.setReferenceId(transfer.getId());
            transactionRepository.save(destLog);
        }
    }
}