package fu.osms.inventory.controller;

import fu.osms.common.dto.ApiResponse;
import fu.osms.inventory.dto.request.StocktakeSessionRequest;
import fu.osms.inventory.dto.response.StocktakeSessionResponse;
import fu.osms.inventory.service.StocktakeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/stocktakes")
@RequiredArgsConstructor
public class StocktakeController {

    private final StocktakeService stocktakeService;

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<StocktakeSessionResponse>> createStocktake(
            @Valid @RequestBody StocktakeSessionRequest request,
            @RequestParam(defaultValue = "false") boolean complete) {
        StocktakeSessionResponse response = stocktakeService.createStocktake(request, complete);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Stocktake created successfully", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<StocktakeSessionResponse>> getStocktakeById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(stocktakeService.getStocktakeById(id)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<Page<StocktakeSessionResponse>>> getStocktakes(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {
        Sort sort = sortDirection.equalsIgnoreCase("ASC")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<StocktakeSessionResponse> response = stocktakeService.getStocktakes(warehouseId, status, keyword, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<StocktakeSessionResponse>> updateStocktake(
            @PathVariable UUID id,
            @Valid @RequestBody StocktakeSessionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(stocktakeService.updateStocktake(id, request)));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<StocktakeSessionResponse>> changeStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, String> request) {
        return ResponseEntity.ok(ApiResponse.success(stocktakeService.changeStatus(id, request.get("status"))));
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStatistics() {
        return ResponseEntity.ok(ApiResponse.success(stocktakeService.getStatistics()));
    }
}
