package fu.osms.inventory.controller;

import fu.osms.common.dto.ApiResponse;
import fu.osms.common.dto.PageResponse;
import fu.osms.inventory.dto.request.InventoryItemRequest;
import fu.osms.inventory.dto.request.InventoryTransactionRequest;
import fu.osms.inventory.dto.response.InventoryItemResponse;
import fu.osms.inventory.dto.response.InventoryTransactionResponse;
import fu.osms.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/items")
    public ResponseEntity<ApiResponse<InventoryItemResponse>> createItem(
            @Valid @RequestBody InventoryItemRequest request) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @GetMapping("/items/{id}")
    public ResponseEntity<ApiResponse<InventoryItemResponse>> getItemById(@PathVariable UUID id) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @GetMapping("/items")
    public ResponseEntity<ApiResponse<PageResponse<InventoryItemResponse>>> getItems(
            @RequestParam UUID warehouseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResponse<InventoryItemResponse> response = inventoryService.getItems(warehouseId, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/warehouses/{warehouseId}/items")
    public ResponseEntity<ApiResponse<PageResponse<InventoryItemResponse>>> getItemsByWarehouse(
            @PathVariable UUID warehouseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResponse<InventoryItemResponse> response = inventoryService.getItems(warehouseId, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/warehouses/{warehouseId}/variants/{variantId}")
    public ResponseEntity<ApiResponse<InventoryItemResponse>> getItemByWarehouseAndVariant(
            @PathVariable UUID warehouseId,
            @PathVariable UUID variantId) {
        InventoryItemResponse response = inventoryService.getItemByWarehouseAndVariant(warehouseId, variantId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/items/low-stock")
    public ResponseEntity<ApiResponse<List<InventoryItemResponse>>> getLowStock() {
        throw new UnsupportedOperationException("Chưa code");
    }

    @PostMapping("/transactions")
    public ResponseEntity<ApiResponse<InventoryTransactionResponse>> recordTransaction(
            @Valid @RequestBody InventoryTransactionRequest request) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<PageResponse<InventoryTransactionResponse>>> getTransactions(
            @RequestParam(required = false) UUID variantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        throw new UnsupportedOperationException("Chưa code");
    }
}
