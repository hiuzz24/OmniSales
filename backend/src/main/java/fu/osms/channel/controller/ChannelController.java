package fu.osms.channel.controller;

import fu.osms.channel.dto.request.ChannelRequest;
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
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Sales channel created successfully", channelService.create(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ChannelResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(channelService.getById(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ChannelResponse>>> getByShop(@RequestParam UUID shopId) {
        return ResponseEntity.ok(ApiResponse.success(channelService.getByShopId(shopId)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ChannelResponse>> update(@PathVariable UUID id,
                                                               @Valid @RequestBody ChannelRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Channel updated successfully", channelService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        channelService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Sales channel deleted successfully", null));
    }

    @GetMapping("/{id}/credential")
    public ResponseEntity<ApiResponse<ChannelCredentialResponse>> getCredential(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(channelService.getCredential(id)));
    }

    @GetMapping("/{id}/products")
    public ResponseEntity<ApiResponse<PageResponse<ChannelProductResponse>>> getProducts(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(channelService.getChannelProducts(id, page, size)));
    }
}
