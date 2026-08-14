package fu.osms.channel.controller;

import fu.osms.channel.dto.request.ChannelRequest;
import fu.osms.channel.dto.request.CreateManualChannelRequest;
import fu.osms.channel.dto.response.ChannelCredentialResponse;
import fu.osms.channel.dto.response.ChannelImportSyncResponse;
import fu.osms.channel.dto.response.ChannelProductResponse;
import fu.osms.channel.dto.response.ChannelResponse;
import fu.osms.channel.service.ChannelService;
import fu.osms.common.dto.ApiResponse;
import fu.osms.common.dto.PageResponse;
import fu.osms.sync.service.ChannelLocalSyncService;
import fu.osms.sync.service.ChannelRemoteSyncService;
import fu.osms.sync.dto.MarketplaceSyncJobResponse;
import fu.osms.sync.service.MarketplaceSyncJobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/channels")
@RequiredArgsConstructor
public class ChannelController {

    private final ChannelService channelService;
    private final ChannelLocalSyncService channelLocalSyncService;
    private final ChannelRemoteSyncService channelRemoteSyncService;
    private final MarketplaceSyncJobService marketplaceSyncJobService;

    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    /** Tạo kênh cấu hình thủ công sau khi xác minh định danh platform. */
    public ResponseEntity<ApiResponse<ChannelResponse>> create(@Valid @RequestBody ChannelRequest request) {
        ChannelResponse response = channelService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'SYSTEM_ADMIN')")
    /** Trả về định danh kênh, trạng thái kết nối và metadata cấu hình an toàn. */
    public ResponseEntity<ApiResponse<ChannelResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(channelService.getById(id)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'SALES', 'OPERATIONS', 'SYSTEM_ADMIN')")
    /** Liệt kê các kênh bán đang hoạt động hoặc có thể kết nối lại. */
    public ResponseEntity<ApiResponse<List<ChannelResponse>>> getAll() {
        List<ChannelResponse> channels = channelService.getAll();
        return ResponseEntity.ok(ApiResponse.success(channels));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    /** Cập nhật cấu hình kênh được phép sửa mà không lộ credentials đã lưu. */
    public ResponseEntity<ApiResponse<ChannelResponse>> update(@PathVariable UUID id,
                                                               @Valid @RequestBody ChannelRequest request) {
        return ResponseEntity.ok(ApiResponse.success(channelService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    /** Ngắt kết nối kênh và dọn token, webhook theo từng platform. */
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        channelService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{id}/credentials")
    @PreAuthorize("hasRole('OWNER')")
    /** Trả về trạng thái credential không nhạy cảm để chẩn đoán kết nối. */
    public ResponseEntity<ApiResponse<ChannelCredentialResponse>> getCredentials(@PathVariable UUID id) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @GetMapping("/{id}/products")
    @PreAuthorize("hasRole('OWNER')")
    /** Liệt kê các product mapping thuộc một kênh. */
    public ResponseEntity<ApiResponse<PageResponse<ChannelProductResponse>>> getProducts(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        throw new UnsupportedOperationException("Chưa code");
    }
    @PostMapping("/{id}/sync")
    /** Chạy chiều đồng bộ mặc định của kênh. */
    public ResponseEntity<ApiResponse<ChannelImportSyncResponse>> sync(@PathVariable UUID id) {
        ChannelImportSyncResponse response = channelLocalSyncService.syncLocalChanges(id);
        return ResponseEntity.ok(ApiResponse.success("Đồng bộ từ ứng dụng lên sàn thành công", response));
    }

    @PostMapping("/{id}/sync/from-app")
    /** Đẩy dữ liệu sản phẩm OSMS lên kênh sàn được chọn. */
    public ResponseEntity<ApiResponse<ChannelImportSyncResponse>> syncFromApp(@PathVariable UUID id) {
        ChannelImportSyncResponse response = channelLocalSyncService.syncLocalChanges(id);
        return ResponseEntity.ok(ApiResponse.success("Đồng bộ từ ứng dụng lên sàn thành công", response));
    }

    @PostMapping("/sync/from-app")
    /** Đẩy các sản phẩm OSMS đủ điều kiện lên tất cả kênh đã kết nối. */
    public ResponseEntity<ApiResponse<ChannelImportSyncResponse>> syncAllFromApp() {
        ChannelImportSyncResponse response = channelLocalSyncService.syncAllLocalChanges();
        return ResponseEntity.ok(ApiResponse.success("Đã đẩy thay đổi lên tất cả sàn đã liên kết", response));
    }

    @PostMapping("/{id}/sync/from-marketplace")
    /** Khởi chạy luồng import từ sàn hiện có; nghiệp vụ vẫn thuộc module importer. */
    public ResponseEntity<ApiResponse<ChannelImportSyncResponse>> syncFromMarketplace(@PathVariable UUID id) {
        ChannelImportSyncResponse response = channelRemoteSyncService.syncRemoteChanges(id);
        return ResponseEntity.ok(ApiResponse.success("Đồng bộ từ sàn về ứng dụng thành công", response));
    }

    @PostMapping("/{id}/sync/from-marketplace/jobs")
    /** Đưa luồng import từ sàn hiện có vào hàng đợi và trả về ID job. */
    public ResponseEntity<ApiResponse<MarketplaceSyncJobResponse>> enqueueSyncFromMarketplace(@PathVariable UUID id) {
        MarketplaceSyncJobResponse response = marketplaceSyncJobService.enqueueRemoteSync(id);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("Đã bắt đầu đồng bộ", response));
    }

    @GetMapping("/sync-jobs/{jobId}")
    /** Đọc tiến độ của job đồng bộ sàn đã được tạo trước đó. */
    public ResponseEntity<ApiResponse<MarketplaceSyncJobResponse>> getSyncJob(@PathVariable UUID jobId) {
        return ResponseEntity.ok(ApiResponse.success(marketplaceSyncJobService.getJob(jobId)));
    }
}
