package fu.osms.sync.order.pull;

import fu.osms.common.dto.ApiResponse;
import fu.osms.sync.dto.SyncLogResponse;
import fu.osms.sync.order.pull.dto.OrderPullRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders/pull")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER', 'SALES')")
public class OrderPullController {
    private final OrderPullService service;

    /** Tạo một job kéo đơn bền vững cho mỗi kênh và trả ngay trạng thái job. */
    @PostMapping
    public ResponseEntity<ApiResponse<List<SyncLogResponse>>> start(@Valid @RequestBody OrderPullRequest request) {
        List<SyncLogResponse> created = service.start(request);
        List<SyncLogResponse> latest = created.stream().map(job -> service.get(job.getId())).toList();
        return ResponseEntity.ok(ApiResponse.success("Order pull jobs started", latest));
    }

    /** Trả về tiến độ mới nhất của một job kéo đơn thủ công. */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SyncLogResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.get(id)));
    }

    /** Liệt kê job kéo đơn đang chờ để frontend tiếp tục polling sau khi chuyển trang. */
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<SyncLogResponse>>> active() {
        return ResponseEntity.ok(ApiResponse.success(service.active()));
    }
}
