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
        throw new UnsupportedOperationException("Chưa code");
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ChannelResponse>> getById(@PathVariable UUID id) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ChannelResponse>>> getAll() {
        throw new UnsupportedOperationException("Chưa code");
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ChannelResponse>> update(@PathVariable UUID id,
                                                               @Valid @RequestBody ChannelRequest request) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        throw new UnsupportedOperationException("Chưa code");
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
