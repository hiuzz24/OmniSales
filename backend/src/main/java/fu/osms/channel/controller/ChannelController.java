package fu.osms.channel.controller;

import fu.osms.channel.dto.request.ChannelRequest;
import fu.osms.channel.dto.request.CreateManualChannelRequest;
import fu.osms.channel.dto.response.ChannelCredentialResponse;
import fu.osms.channel.dto.response.ChannelProductResponse;
import fu.osms.channel.dto.response.ChannelResponse;
import fu.osms.channel.service.ChannelService;
import fu.osms.common.dto.ApiResponse;
import fu.osms.common.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/channels")
@RequiredArgsConstructor
public class ChannelController {

    private final ChannelService channelService;

    @PostMapping
    public ResponseEntity<ApiResponse<ChannelResponse>> create(@Valid @RequestBody ChannelRequest request) {
        ChannelResponse response = channelService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/manual")
    public ResponseEntity<ApiResponse<ChannelResponse>> createManual(@Valid @RequestBody CreateManualChannelRequest request) {
        ChannelResponse response = channelService.createManual(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ChannelResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(channelService.getById(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ChannelResponse>>> getAll() {
        List<ChannelResponse> channels = channelService.getAll();
        return ResponseEntity.ok(ApiResponse.success(channels));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ChannelResponse>> update(@PathVariable UUID id,
                                                               @Valid @RequestBody ChannelRequest request) {
        return ResponseEntity.ok(ApiResponse.success(channelService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        channelService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{id}/credentials")
    public ResponseEntity<ApiResponse<ChannelCredentialResponse>> getCredentials(@PathVariable UUID id) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @GetMapping("/{id}/products")
    public ResponseEntity<ApiResponse<PageResponse<ChannelProductResponse>>> getProducts(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        throw new UnsupportedOperationException("Chưa code");
    }
}
