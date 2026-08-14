package fu.osms.catalog.controller;

import fu.osms.catalog.dto.request.ProductRequest;
import fu.osms.catalog.dto.response.ProductImportResult;
import fu.osms.catalog.dto.response.ProductResponse;
import fu.osms.catalog.dto.response.ProductSyncQueuedResponse;
import fu.osms.catalog.enums.ProductStatus;
import fu.osms.catalog.service.ProductImportService;
import fu.osms.catalog.service.ProductService;
import fu.osms.common.dto.ApiResponse;
import fu.osms.common.dto.PageResponse;
import fu.osms.common.enums.PlatformType;
import fu.osms.sync.dto.SyncResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductImportService productImportService;

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'SALES')")
    /** Tạo sản phẩm OSMS cùng các biến thể và ảnh nội bộ. */
    public ResponseEntity<ApiResponse<ProductResponse>> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse productResponse = productService.create(request);
        return ResponseEntity.ok(ApiResponse.success("Tạo sản phẩm thành công",productResponse));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES', 'OPERATIONS')")
    /** Trả về đầy đủ chi tiết sản phẩm cho màn xem và chỉnh sửa. */
    public ResponseEntity<ApiResponse<ProductResponse>> getById(@PathVariable UUID id) {
        ProductResponse response = productService.getById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'SALES', 'OPERATIONS')")
    /** Tìm sản phẩm theo trạng thái, platform và bộ lọc phân trang. */
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> getProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) PlatformType platform,
            @RequestParam(required = false) String platforms,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size) {
        Collection<PlatformType> platformFilters = parsePlatforms(platforms);
        if (platform != null && !platformFilters.contains(platform)) {
            platformFilters = new java.util.LinkedHashSet<>(platformFilters);
            platformFilters.add(platform);
        }
        PageResponse<ProductResponse> response = productService.search(keyword, status, platformFilters, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private Collection<PlatformType> parsePlatforms(String platforms) {
        if (platforms == null || platforms.isBlank()) {
            return List.of();
        }
        return Arrays.stream(platforms.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> {
                    try {
                        return PlatformType.valueOf(value.toUpperCase());
                    } catch (IllegalArgumentException ex) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES')")
    /** Cập nhật catalog nội bộ nhưng giữ nguyên dữ liệu mapping do kênh quản lý. */
    public ResponseEntity<ApiResponse<ProductResponse>> update(@PathVariable UUID id,
                                                               @Valid @RequestBody ProductRequest request) {
        ProductResponse response = productService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật sản phẩm thành công", response));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES')")
    /** Thay đổi trạng thái cho phép bán và đồng bộ của sản phẩm. */
    public ResponseEntity<ApiResponse<ProductResponse>> updateStatus(@PathVariable UUID id,
                                                                     @RequestParam ProductStatus status) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @DeleteMapping("/{id}/delete")
    @PreAuthorize("hasRole('OWNER')")
    /** Xóa mềm sản phẩm để tham chiếu từ đơn hàng cũ vẫn hợp lệ. */
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        productService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{productId}/sync")
    @PreAuthorize("hasRole('OWNER')")
    /** Đồng bộ một sản phẩm lên tất cả kênh đã cấu hình theo luồng đồng bộ. */
    public ResponseEntity<ApiResponse<SyncResult>> syncProduct(@PathVariable UUID productId) {
        SyncResult result = productService.syncProductToAllChannels(productId);
        return ResponseEntity.ok(ApiResponse.success("Sync triggered", result));
    }

    @PostMapping("/{productId}/channels/{channelId}/sync")
    @PreAuthorize("hasRole('OWNER')")
    /** Chỉ đồng bộ một sản phẩm lên kênh được chọn. */
    public ResponseEntity<ApiResponse<SyncResult>> syncProductToChannel(
            @PathVariable UUID productId,
            @PathVariable UUID channelId) {
        SyncResult result = productService.syncProductToChannel(productId, channelId);
        return ResponseEntity.ok(ApiResponse.success("Channel sync triggered", result));
    }

    @PostMapping("/{productId}/sync/async")
    @PreAuthorize("hasRole('OWNER')")
    /** Đưa yêu cầu đồng bộ tất cả kênh vào hàng đợi và trả về ID theo dõi. */
    public ResponseEntity<ApiResponse<ProductSyncQueuedResponse>> syncProductToAllChannelsAsync(
            @PathVariable UUID productId) {
        ProductSyncQueuedResponse response = productService.syncProductToAllChannelsAsync(productId);
        return ResponseEntity.accepted().body(ApiResponse.success("Sync queued", response));
    }

    @PostMapping("/{productId}/channels/{channelId}/sync/async")
    @PreAuthorize("hasRole('OWNER')")
    /** Đưa yêu cầu đồng bộ một kênh vào hàng đợi và trả về ID theo dõi. */
    public ResponseEntity<ApiResponse<ProductSyncQueuedResponse>> syncProductToChannelAsync(
            @PathVariable UUID productId,
            @PathVariable UUID channelId) {
        ProductSyncQueuedResponse response = productService.syncProductToChannelAsync(productId, channelId);
        return ResponseEntity.accepted().body(ApiResponse.success("Channel sync queued", response));
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
