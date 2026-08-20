package fu.osms.inventory.addressmatching.controller;

import fu.osms.common.dto.ApiResponse;
import fu.osms.inventory.addressmatching.AddressMatchResult;
import fu.osms.inventory.addressmatching.dto.request.AddressComparisonRequest;
import fu.osms.inventory.addressmatching.dto.request.AddressGroupRequest;
import fu.osms.inventory.addressmatching.dto.response.AddressComparisonResponse;
import fu.osms.inventory.addressmatching.dto.response.AddressGroupResponse;
import fu.osms.inventory.addressmatching.service.AddressComparisonService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/address")
@RequiredArgsConstructor
public class AddressComparisonController {

    private final AddressComparisonService addressComparisonService;

    @PostMapping("/compare")
    public ResponseEntity<ApiResponse<AddressComparisonResponse>> compare(
            @Valid @RequestBody AddressComparisonRequest request) {
        AddressMatchResult result = addressComparisonService.compare(
                request.getAddress1(), request.getAddress2());

        AddressComparisonResponse response = toComparisonResponse(result);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/group")
    public ResponseEntity<ApiResponse<AddressGroupResponse>> group(
            @Valid @RequestBody AddressGroupRequest request) {
        AddressGroupResponse response = addressComparisonService.groupAddresses(
                request.getAddresses());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private AddressComparisonResponse toComparisonResponse(AddressMatchResult result) {
        Map<String, AddressComparisonResponse.ComponentResultResponse> components = new LinkedHashMap<>();

        result.getComponentResults().forEach((key, val) ->
                components.put(key, AddressComparisonResponse.ComponentResultResponse.builder()
                        .matched(val.isMatched())
                        .score(val.getScore())
                        .build())
        );

        return AddressComparisonResponse.builder()
                .matched(result.isMatched())
                .score(result.getScore())
                .level(result.getLevel().name())
                .normalizedAddress1(result.getNormalizedAddress1())
                .normalizedAddress2(result.getNormalizedAddress2())
                .components(components)
                .conflicts(result.getConflicts())
                .build();
    }
}
