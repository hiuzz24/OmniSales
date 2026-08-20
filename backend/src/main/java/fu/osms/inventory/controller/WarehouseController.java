package fu.osms.inventory.controller;

import fu.osms.common.dto.ApiResponse;
import fu.osms.inventory.dto.request.WarehouseAddressSyncConfirmRequest;
import fu.osms.inventory.dto.request.WarehouseMarketplaceSyncRequest;
import fu.osms.inventory.dto.request.WarehouseRequest;
import fu.osms.inventory.dto.response.WarehouseAddressComparisonResult;
import fu.osms.inventory.dto.response.WarehouseMarketplaceSyncResult;
import fu.osms.inventory.dto.response.WarehouseResponse;
import fu.osms.inventory.service.WarehouseService;
import fu.osms.inventory.service.WarehouseSyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;
    private final WarehouseSyncService warehouseSyncService;

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<WarehouseResponse>> create(@Valid @RequestBody WarehouseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(warehouseService.create(request)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<WarehouseResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.getById(id)));
    }

    @GetMapping("/master")
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<WarehouseResponse>> getMaster() {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.getMaster()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<List<WarehouseResponse>>> getAll(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        List<WarehouseResponse> warehouses = warehouseService.getAll(keyword, status);
        return ResponseEntity.ok(ApiResponse.success(warehouses));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<WarehouseResponse>> update(@PathVariable UUID id,
                                                                 @Valid @RequestBody WarehouseRequest request) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.update(id, request)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('OWNER', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<WarehouseResponse>> toggleStatus(@PathVariable UUID id,
                                                                       @RequestBody java.util.Map<String, Boolean> body) {
        Boolean isActive = body.getOrDefault("isActive", true);
        return ResponseEntity.ok(ApiResponse.success(warehouseService.toggleStatus(id, isActive)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        warehouseService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/userWarehouse/{id}")
    public ResponseEntity<ApiResponse<WarehouseResponse>> getUserWarehouseById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.getWarehouseByUserId(id)));
    }

    /**
     * Sync warehouse name, address, and contact info to all connected marketplace channels.
     * Each channel (Shopify, Lazada, TikTok) is attempted independently so a failure on one
     * does not block the others. The response contains per-channel results.
     */
    @PostMapping("/{id}/sync-to-marketplaces")
    @PreAuthorize("hasAnyRole('OWNER', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<WarehouseMarketplaceSyncResult>> syncToMarketplaces(
            @PathVariable UUID id,
            @Valid @RequestBody WarehouseMarketplaceSyncRequest request) {
        WarehouseMarketplaceSyncResult result = warehouseSyncService.syncToMarketplaces(id, request);
        String message = result.isAllSucceeded()
                ? "Đã cập nhật kho hàng lên tất cả các sàn thành công."
                : "Cập nhật kho hàng hoàn tất — một số sàn gặp lỗi, vui lòng kiểm tra chi tiết.";
        return ResponseEntity.ok(ApiResponse.success(message, result));
    }

    /**
     * Compare default warehouse addresses across all connected marketplace platforms
     * with the current master warehouse address.
     */
    @GetMapping("/compare-addresses")
    @PreAuthorize("hasAnyRole('OWNER', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<WarehouseAddressComparisonResult>> compareAddresses() {
        WarehouseAddressComparisonResult result = warehouseSyncService.comparePlatformAddresses();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * Apply warehouse address sync: create a new warehouse with the synced address
     * from platforms and deactivate the old one. Documents keep the old warehouse reference.
     */
    @PostMapping("/apply-address-sync")
    @PreAuthorize("hasAnyRole('OWNER', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> applyAddressSync(
            @RequestBody WarehouseAddressSyncConfirmRequest request) {
        warehouseSyncService.applyAddressSync(request.isConfirm());
        String message = request.isConfirm()
                ? "Đã đồng bộ địa chỉ kho hàng thành công."
                : "Bạn chưa xác nhận đồng bộ.";
        return ResponseEntity.ok(ApiResponse.success(message, null));
    }
}
