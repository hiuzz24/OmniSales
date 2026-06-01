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
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/items")
    public ResponseEntity<ApiResponse<InventoryItemResponse>> createItem(
            @Valid @RequestBody InventoryItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo mục tồn kho thành công", inventoryService.createItem(request)));
    }

    @GetMapping("/items/{id}")
    public ResponseEntity<ApiResponse<InventoryItemResponse>> getItemById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.getItemById(id)));
    }

    @GetMapping("/items")
    public ResponseEntity<ApiResponse<PageResponse<InventoryItemResponse>>> getItemsByShop(
            @RequestParam UUID shopId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.getItemsByShop(shopId, page, size)));
    }

    @GetMapping("/items/low-stock")
    public ResponseEntity<ApiResponse<List<InventoryItemResponse>>> getLowStock(@RequestParam UUID shopId) {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.getLowStockItems(shopId)));
    }

    @PostMapping("/transactions")
    public ResponseEntity<ApiResponse<InventoryTransactionResponse>> recordTransaction(
            @Valid @RequestBody InventoryTransactionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Ghi nhận giao dịch kho thành công",
                        inventoryService.recordTransaction(request)));
    }

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<PageResponse<InventoryTransactionResponse>>> getTransactions(
            @RequestParam UUID shopId,
            @RequestParam(required = false) UUID variantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<InventoryTransactionResponse> result = variantId != null
                ? inventoryService.getTransactionsByVariant(shopId, variantId, page, size)
                : inventoryService.getTransactionsByShop(shopId, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
