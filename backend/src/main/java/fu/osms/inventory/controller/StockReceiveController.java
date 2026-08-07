package fu.osms.inventory.controller;

import fu.osms.auth.repository.UserRepository;
import fu.osms.common.dto.ApiResponse;
import fu.osms.common.dto.PageResponse;
import fu.osms.inventory.dto.request.ManualStockReceiveRequest;
import fu.osms.inventory.dto.request.StockReceiveRequest;
import fu.osms.inventory.dto.request.ConfirmExtraItemsRequest;
import fu.osms.inventory.dto.response.PreviewRowDTO;
import fu.osms.inventory.dto.response.StockInImportResultDTO;
import fu.osms.inventory.dto.response.StockReceiveResponse;
import fu.osms.inventory.service.StockReceiveExtraItemImportService;
import fu.osms.inventory.service.StockReceiveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/receipts")
@RequiredArgsConstructor
public class StockReceiveController {

    private final StockReceiveService stockReceiveService;
    private final StockReceiveExtraItemImportService extraItemImportService;
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

    @PostMapping("/manual")
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<StockReceiveResponse>> createManualReceipt(
            @Valid @RequestBody ManualStockReceiveRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID userId = resolveUserId(userDetails);
        StockReceiveResponse response = stockReceiveService.createManualReceipt(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo phiếu nhập thủ công thành công", response));
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

    @PostMapping("/sync-marketplace-inventory")
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncPendingMarketplaceInventory() {
        int syncedVariantCount = stockReceiveService.syncPendingMarketplaceInventory();
        return ResponseEntity.ok(ApiResponse.success(
                "Đồng bộ tồn có thể bán và giá phiếu nhập lên các sàn thành công",
                Map.of("syncedVariantCount", syncedVariantCount)
        ));
    }

    @PostMapping("/{id}/sync-marketplace-inventory")
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncReceiptMarketplaceInventory(@PathVariable UUID id) {
        int syncedVariantCount = stockReceiveService.syncReceiptMarketplaceInventory(id);
        return ResponseEntity.ok(ApiResponse.success(
                "Đồng bộ tồn có thể bán và giá phiếu nhập lên các sàn đang bán thành công",
                Map.of("syncedVariantCount", syncedVariantCount)
        ));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<StockReceiveResponse>> getReceiptById(@PathVariable UUID id) {
        StockReceiveResponse response = stockReceiveService.getReceiptById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}/import-extra-items/template")
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS')")
    public ResponseEntity<byte[]> downloadExtraItemsTemplate(@PathVariable UUID id) throws IOException {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=stock-in-extra-items-template.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(extraItemImportService.createTemplate(id));
    }

    @GetMapping("/import-extra-items/template")
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS')")
    public ResponseEntity<byte[]> downloadExtraItemsTemplateForNewReceipt() throws IOException {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=stock-in-extra-items-template.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(extraItemImportService.createTemplateForNewReceipt());
    }

    @PostMapping(value = "/{id}/import-extra-items/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<java.util.List<PreviewRowDTO>>> previewExtraItems(
            @PathVariable UUID id, @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(ApiResponse.success(extraItemImportService.preview(id, file)));
    }

    @PostMapping("/{id}/import-extra-items/confirm")
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<StockInImportResultDTO>> confirmExtraItems(
            @PathVariable UUID id, @RequestBody ConfirmExtraItemsRequest request) throws IOException {
        return ResponseEntity.ok(ApiResponse.success("Da xu ly import san pham bo sung", extraItemImportService.confirm(id, request)));
    }

    @GetMapping("/import-extra-items/errors/{fileName:.+}")
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS')")
    public ResponseEntity<byte[]> downloadExtraItemsErrorFile(@PathVariable String fileName) throws IOException {
        Path file = extraItemImportService.resolveErrorFile(fileName);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(Files.readAllBytes(file));
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
