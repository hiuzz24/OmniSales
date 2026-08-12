package fu.osms.channel.controller;

import fu.osms.channel.dto.response.ChannelConnectionLogResponse;
import fu.osms.channel.enums.ChannelConnectionAction;
import fu.osms.channel.enums.ChannelConnectionLogStatus;
import fu.osms.channel.service.ChannelConnectionLogService;
import fu.osms.common.dto.ApiResponse;
import fu.osms.common.dto.PageResponse;
import fu.osms.common.enums.PlatformType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/channel-connection-logs")
@RequiredArgsConstructor
public class ChannelConnectionLogController {

    private final ChannelConnectionLogService channelConnectionLogService;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<ChannelConnectionLogResponse>>> getLogs(
            @RequestParam(required = false) PlatformType platform,
            @RequestParam(required = false) ChannelConnectionLogStatus status,
            @RequestParam(required = false) ChannelConnectionAction action,
            @RequestParam(required = false) UUID channelId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageResponse<ChannelConnectionLogResponse> response =
                channelConnectionLogService.search(platform, status, action, channelId, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
