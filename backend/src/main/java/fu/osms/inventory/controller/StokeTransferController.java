package fu.osms.inventory.controller;

import fu.osms.inventory.dto.request.StockTransferRequest;
import fu.osms.inventory.dto.response.AvailableVariantDTO;
import fu.osms.inventory.dto.response.TransferInventoryListResponse;
import fu.osms.inventory.mapper.AvailableVariantDTOMapper;
import fu.osms.inventory.service.InventoryService;
import fu.osms.inventory.service.StokeTransferService;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/transfer")
public class StokeTransferController {

    private final StokeTransferService stokeTransferService;
    private final InventoryService inventoryService;


    @GetMapping
    public ResponseEntity<TransferInventoryListResponse> getTransferList(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        TransferInventoryListResponse data = stokeTransferService.getTransferListData(status, warehouseId, keyword, page, size);
        return ResponseEntity.ok(data);
    }

    @GetMapping("/available-variants")
    public ResponseEntity<Map<String, Object>> getAvailableVariantsByWarehouse(
            @RequestParam("warehouseId") UUID warehouseId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "200") int size) {
        Map<String, Object> response = new HashMap<>();

        try {
            fu.osms.common.dto.PageResponse<AvailableVariantDTO> data =
                    inventoryService.getAvailableVariantsByWarehousePaged(warehouseId, keyword, page, size);

            response.put("success", true);
            response.put("message", "Loaded warehouse inventory products successfully.");
            response.put("data", data);

            return ResponseEntity.ok(response);

        } catch (fu.osms.common.exception.AppException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "An unexpected error occurred: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    @GetMapping("/suggested-code")
    public ResponseEntity<String> getSuggestedTransferCode() {
        String suggestedCode = stokeTransferService.generateTransferCode();
        return ResponseEntity.ok(suggestedCode);
    }
    @PostMapping
    public ResponseEntity<Map<String, Object>> createTransferInventory(@Valid @RequestBody StockTransferRequest request) {

        Map<String, Object> response = new HashMap<>();
        try {
            stokeTransferService.createTransferInventory(request);

            response.put("success", true);
            response.put("message", "Transfer inventory created successfully.");
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {


            // Bắt các lỗi nghiệp vụ chủ động ném ra từ Service (trùng kho, vượt quá tồn kho,...)
            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(response);

        } catch (Exception e) {

            // Xử lý các lỗi hệ thống không lường trước
            response.put("success", false);
            response.put("message", "An unexpected error occurred: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getTransferDetail(@PathVariable UUID id) {
        Map<String, Object> response = new HashMap<>();
        try {
            var data = stokeTransferService.getTransferDetail(id);
            response.put("success", true);
            response.put("data", data);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> updateStatus(
            @PathVariable UUID id,
            @RequestParam String status,
            @RequestParam UUID userId) {
        Map<String, Object> response = new HashMap<>();
        try {
            stokeTransferService.updateStatus(id, status, userId);
            response.put("success", true);
            response.put("message", "Cập nhật trạng thái thành công.");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Lỗi: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
