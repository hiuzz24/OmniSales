package fu.osms.catalog.controller;

import fu.osms.catalog.dto.request.ProductRequest;
import fu.osms.catalog.dto.response.ProductImportResult;
import fu.osms.catalog.dto.response.ProductResponse;
import fu.osms.catalog.dto.response.ProductInsightsResponse;
import fu.osms.catalog.enums.ProductStatus;
import fu.osms.catalog.service.ProductImportService;
import fu.osms.catalog.service.ProductService;
import fu.osms.catalog.service.ProductInsightsService;
import fu.osms.common.dto.ApiResponse;
import fu.osms.common.dto.PageResponse;
import fu.osms.common.enums.PlatformType;
import fu.osms.sync.dto.SyncResult;
import fu.osms.inventory.dto.response.InventoryTransactionDTO;
import fu.osms.inventory.service.InventoryTransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Slf4j
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductImportService productImportService;
    private final ProductInsightsService productInsightsService;
    private final InventoryTransactionService inventoryTransactionService;

    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse productResponse = productService.create(request);
        return ResponseEntity.ok(ApiResponse.success("Tạo sản phẩm thành công",productResponse));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getById(@PathVariable UUID id) {
        ProductResponse response = productService.getById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}/insights")
    public ResponseEntity<ApiResponse<ProductInsightsResponse>> getInsights(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(productInsightsService.getInsights(id)));
    }

    @GetMapping("/{id}/inventory-transactions")
    public ResponseEntity<ApiResponse<PageResponse<InventoryTransactionDTO>>> getInventoryTransactions(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        PageRequest pageRequest = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Direction.DESC, "performedAt")
        );
        return ResponseEntity.ok(ApiResponse.success(
                inventoryTransactionService.getTransactionsDTOByProduct(id, pageRequest)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> getProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) PlatformType platform,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size) {
        PageResponse<ProductResponse> response = productService.search(keyword, status, platform, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> update(@PathVariable UUID id,
                                                               @Valid @RequestBody ProductRequest request) {
        ProductResponse response = productService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật sản phẩm thành công", response));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<ProductResponse>> updateStatus(@PathVariable UUID id,
                                                                     @RequestParam ProductStatus status) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @DeleteMapping("/{id}/delete")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        productService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{productId}/sync")
    public ResponseEntity<ApiResponse<SyncResult>> syncProduct(@PathVariable UUID productId) {
        SyncResult result = productService.syncProductToAllChannels(productId);
        return ResponseEntity.ok(ApiResponse.success("Sync triggered", result));
    }

    @PostMapping("/{productId}/channels/{channelId}/sync")
    public ResponseEntity<ApiResponse<SyncResult>> syncProductToChannel(
            @PathVariable UUID productId,
            @PathVariable UUID channelId) {
        SyncResult result = productService.syncProductToChannel(productId, channelId);
        return ResponseEntity.ok(ApiResponse.success("Channel sync triggered", result));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProductImportResult>> importExcel(
            @RequestParam("file") MultipartFile file) {
        log.info("Importing products from file: {} ({} bytes)",
                file.getOriginalFilename(), file.getSize());
        ProductImportResult result = productImportService.importFromExcel(file);
        return ResponseEntity.ok(ApiResponse.success(
                String.format("Import thành công: %d tạo mới, %d cập nhật", result.getCreatedCount(), result.getUpdatedCount()),
                result));
    }
}
