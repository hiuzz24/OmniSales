package fu.osms.customer.controller;

import fu.osms.common.dto.ApiResponse;
import fu.osms.common.dto.PageResponse;
import fu.osms.customer.dto.request.CustomerRequest;
import fu.osms.customer.dto.response.CustomerResponse;
import fu.osms.customer.dto.response.CustomerStatsResponse;
import fu.osms.customer.dto.response.PageWithOrderCustomersResponse;
import fu.osms.customer.dto.response.SyncOrdersResponse;
import fu.osms.customer.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping
    public ResponseEntity<ApiResponse<CustomerResponse>> create(@Valid @RequestBody CustomerRequest request) {
        CustomerResponse customer = customerService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo khách hàng thành công", customer));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerResponse>> getById(@PathVariable UUID id) {
        CustomerResponse customer = customerService.getById(id);
        return ResponseEntity.ok(ApiResponse.success(customer));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<CustomerResponse>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String gender) {
        PageResponse<CustomerResponse> customers = customerService.getAll(page, size, search, status, gender);
        return ResponseEntity.ok(ApiResponse.success(customers));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<CustomerStatsResponse>> getStats() {
        CustomerStatsResponse stats = customerService.getStats();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/page-with-order-customers")
    public ResponseEntity<ApiResponse<PageWithOrderCustomersResponse>> getPageWithOrderCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String gender) {
        PageWithOrderCustomersResponse result = customerService.getPageWithOrderCustomers(page, size, search, status, gender);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/sync-from-orders")
    public ResponseEntity<ApiResponse<SyncOrdersResponse>> syncFromOrders() {
        int updated = customerService.syncCustomersFromOrders();
        return ResponseEntity.ok(ApiResponse.success(
                "Đã đồng bộ " + updated + " đơn hàng về khách hàng",
                SyncOrdersResponse.builder()
                        .updatedCount(updated)
                        .build()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerResponse>> update(@PathVariable UUID id,
                                                              @Valid @RequestBody CustomerRequest request) {
        CustomerResponse customer = customerService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật khách hàng thành công", customer));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        customerService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa khách hàng thành công", null));
    }
}
