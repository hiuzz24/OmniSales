package fu.osms.notification.controller;

import fu.osms.common.dto.ApiResponse;
import fu.osms.common.dto.PageResponse;
import fu.osms.notification.dto.response.NotificationResponse;
import fu.osms.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NotificationResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.getById(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> getByUser(
            @RequestParam UUID shopId,
            @RequestParam UUID userId,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<NotificationResponse> result = unreadOnly
                ? notificationService.getUnread(shopId, userId, page, size)
                : notificationService.getByUser(shopId, userId, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Long>> countUnread(@RequestParam UUID shopId,
                                                          @RequestParam UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.countUnread(shopId, userId)));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markAsRead(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Marked as read successfully",
                notificationService.markAsRead(id)));
    }

    @PostMapping("/read-all")
    public ResponseEntity<ApiResponse<Integer>> markAllAsRead(@RequestParam UUID shopId,
                                                               @RequestParam UUID userId) {
        int count = notificationService.markAllAsRead(shopId, userId);
        return ResponseEntity.ok(ApiResponse.success("All notifications marked as read successfully", count));
    }
}
