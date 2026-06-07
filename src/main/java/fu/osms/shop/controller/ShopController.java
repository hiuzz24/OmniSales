package fu.osms.shop.controller;

import fu.osms.common.dto.ApiResponse;
import fu.osms.common.dto.PageResponse;
import fu.osms.shop.dto.request.ShopRequest;
import fu.osms.shop.dto.response.ShopResponse;
import fu.osms.shop.service.ShopService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/shops")
@RequiredArgsConstructor
public class ShopController {

    private final ShopService shopService;

    @PostMapping
    public ResponseEntity<ApiResponse<ShopResponse>> create(@Valid @RequestBody ShopRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Shop created successfully", shopService.create(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ShopResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(shopService.getById(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ShopResponse>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(shopService.getAll(page, size)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ShopResponse>> update(@PathVariable UUID id,
                                                             @Valid @RequestBody ShopRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Shop updated successfully", shopService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        shopService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Shop deleted successfully", null));
    }
}
