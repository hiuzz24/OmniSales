package fu.osms.sync.controller;

import fu.osms.common.dto.ApiResponse;
import fu.osms.common.dto.PageResponse;
import fu.osms.common.enums.PlatformType;
import fu.osms.sync.dto.WebhookEventResponse;
import fu.osms.sync.dto.WebhookReceiveResult;
import fu.osms.sync.service.WebhookReceiverService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookReceiverService webhookReceiverService;
    @PostMapping(value = "/api/webhooks/{platform}",consumes = MediaType.ALL_VALUE)
    public ResponseEntity<ApiResponse<WebhookReceiveResult>> receiveByPlatform(
            @PathVariable String platform,
            @RequestBody String rawBody,
            HttpServletRequest request
    ) {
        PlatformType platformType = PlatformType.valueOf(platform.toUpperCase());
        WebhookReceiveResult result = webhookReceiverService.receive(platformType, extractHeaders(request), rawBody);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/api/webhook-events")
    public ResponseEntity<ApiResponse<PageResponse<WebhookEventResponse>>> getWebhookEvents(
            @RequestParam(required = false) PlatformType platform,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) UUID channelId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageResponse<WebhookEventResponse> response =
                webhookReceiverService.search(platform, status, eventType, channelId, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        for (String name : Collections.list(names)) {
            headers.put(name.toLowerCase(), request.getHeader(name));
        }
        return headers;
    }
}
