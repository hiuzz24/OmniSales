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

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> getNotifications(
            @RequestParam UUID userId,
            @RequestParam(required = false, defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Long>> countUnread(@RequestParam UUID userId) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable UUID id) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @PostMapping("/mark-all-read")
    public ResponseEntity<ApiResponse<Integer>> markAllAsRead(@RequestParam UUID userId) {
        throw new UnsupportedOperationException("Chưa code");
    }
}
