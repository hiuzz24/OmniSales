package fu.osms.inventory.controller;

import fu.osms.common.dto.ApiResponse;
import fu.osms.common.dto.PageResponse;
import fu.osms.inventory.dto.request.InventoryItemRequest;
import fu.osms.inventory.dto.request.InventoryTransactionRequest;
import fu.osms.inventory.dto.response.InventoryDetailDTO;
import fu.osms.inventory.dto.response.InventoryItemResponse;
import fu.osms.inventory.dto.response.InventoryTransactionDTO;
import fu.osms.inventory.dto.response.InventoryTransactionResponse;
import fu.osms.inventory.service.InventoryService;
import fu.osms.inventory.service.InventoryTransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    private final InventoryTransactionService transactionService;

    @GetMapping("/category/{id}")
    public ResponseEntity<ApiResponse<PageResponse<InventoryItemResponse>>> getInventoryByCategoryId(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name())
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        PageRequest pageRequest = PageRequest.of(page, size, sort);

        PageResponse<InventoryItemResponse> inventoryPage = inventoryService.getInventoryByCategoryId(id, pageRequest, page, size);

        ApiResponse<PageResponse<InventoryItemResponse>> response = ApiResponse.<PageResponse<InventoryItemResponse>>builder()
                .success(true)
                .message("Tải danh sách tồn kho theo danh mục thành công")
                .data(inventoryPage)
                .build();

        return ResponseEntity.ok(response);
    }
    @PostMapping("/items")
    public ResponseEntity<ApiResponse<InventoryItemResponse>> createItem(
            @Valid @RequestBody InventoryItemRequest request) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @GetMapping("/items/{id}")
    public ResponseEntity<ApiResponse<InventoryItemResponse>> getItemById(@PathVariable UUID id) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<InventoryItemResponse>>> getInventoryList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name())
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        PageRequest pageRequest = PageRequest.of(page, size, sort);
        PageResponse<InventoryItemResponse> inventoryPage = inventoryService.getAllInventoryItems(pageRequest, page, size);
        ApiResponse<PageResponse<InventoryItemResponse>> response = ApiResponse.<PageResponse<InventoryItemResponse>>builder()
                .success(true)
                .message("Tải danh sách tồn kho thành công")
                .data(inventoryPage)
                .build();

        return ResponseEntity.ok(response);
    }



    @GetMapping("/detail/transactions")
    public ResponseEntity<ApiResponse<PageResponse<InventoryTransactionDTO>>> getTransactions(
            @RequestParam(required = false) UUID variantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "performedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        if (variantId == null) {
            ApiResponse<PageResponse<InventoryTransactionDTO>> errorResponse = ApiResponse.<PageResponse<InventoryTransactionDTO>>builder()
                    .success(false)
                    .message("ID của biến thể sản phẩm không được để trống")
                    .data(null)
                    .build();
            return ResponseEntity.badRequest().body(errorResponse);
        }
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name())
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        PageRequest pageRequest = PageRequest.of(page, size, sort);

        PageResponse<InventoryTransactionDTO> transactionPage =
                transactionService.getTransactionsDTOByVariant(variantId, pageRequest, page, size);

        ApiResponse<PageResponse<InventoryTransactionDTO>> response = ApiResponse.<PageResponse<InventoryTransactionDTO>>builder()
                .success(true)
                .message("Tải lịch sử biến động kho của sản phẩm thành công")
                .data(transactionPage)
                .build();

        return ResponseEntity.ok(response);
    }
    @GetMapping("/detail/{id}")
    public ResponseEntity<ApiResponse<InventoryDetailDTO>> getInventoryItemDetail(@PathVariable UUID id) {

        InventoryDetailDTO detailDTO = inventoryService.getInventoryItemDetail(id);

        ApiResponse<InventoryDetailDTO> response = ApiResponse.<InventoryDetailDTO>builder()
                .success(true)
                .message("Tải thông tin chi tiết tồn kho sản phẩm thành công")
                .data(detailDTO)
                .build();

        return ResponseEntity.ok(response);
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
        throw new UnsupportedOperationException("Not implemented");
    }

    @PostMapping("/transactions")
    public ResponseEntity<ApiResponse<InventoryTransactionResponse>> recordTransaction(
            @Valid @RequestBody InventoryTransactionRequest request) {
        InventoryTransactionResponse response = inventoryService.recordTransaction(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<PageResponse<InventoryTransactionResponse>>> getTransactions(
            @RequestParam(required = false) UUID variantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
