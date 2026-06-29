package fu.osms.inventory.controller;

import fu.osms.auth.repository.UserRepository;
import fu.osms.common.dto.ApiResponse;
import fu.osms.common.dto.PageResponse;
import fu.osms.inventory.dto.request.StockReceiveRequest;
import fu.osms.inventory.dto.response.StockReceiveResponse;
import fu.osms.inventory.service.StockReceiveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/receipts")
@RequiredArgsConstructor
public class StockReceiveController {

    private final StockReceiveService stockReceiveService;
    private final UserRepository userRepository;

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<StockReceiveResponse>> createReceipt(
            @Valid @RequestBody StockReceiveRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID userId = resolveUserId(userDetails);
        StockReceiveResponse response = stockReceiveService.createReceipt(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo phiếu nhập thành công", response));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<PageResponse<StockReceiveResponse>>> getReceipts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageResponse<StockReceiveResponse> receipts = stockReceiveService.getReceipts(page, size);
        return ResponseEntity.ok(ApiResponse.success(receipts));
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<Object>> getReceiptStatistics() {
        Object statistics = stockReceiveService.getReceiptStatistics();
        return ResponseEntity.ok(ApiResponse.success("Thống kê phiếu nhập thành công", statistics));
    }

    @GetMapping("/next-code")
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<String>> getNextReceiptCode() {
        return ResponseEntity.ok(ApiResponse.success(stockReceiveService.getNextReceiptCode()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<StockReceiveResponse>> getReceiptById(@PathVariable UUID id) {
        StockReceiveResponse response = stockReceiveService.getReceiptById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<StockReceiveResponse>> updateReceipt(
            @PathVariable UUID id,
            @Valid @RequestBody StockReceiveRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        UUID userId = resolveUserId(userDetails);
        StockReceiveResponse response = stockReceiveService.updateReceipt(id, request, userId);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật phiếu nhập thành công", response));
    }

    @PatchMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<StockReceiveResponse>> completeReceipt(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        UUID userId = resolveUserId(userDetails);
        StockReceiveResponse response = stockReceiveService.completeReceipt(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Hoàn thành phiếu nhập thành công", response));
    }

    private UUID resolveUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .map(user -> user.getId())
                .orElse(null);
    }
}
