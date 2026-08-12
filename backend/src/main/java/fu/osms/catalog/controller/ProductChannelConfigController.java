package fu.osms.catalog.controller;

import fu.osms.catalog.dto.request.ChannelConfigRequest;
import fu.osms.catalog.dto.request.CategorySuggestionRequest;
import fu.osms.catalog.dto.response.ChannelProductConfigResponse;
import fu.osms.catalog.dto.response.PlatformAttributeResponse;
import fu.osms.catalog.dto.response.PlatformCategoryNodeResponse;
import fu.osms.catalog.dto.response.PlatformBrandResponse;
import fu.osms.catalog.dto.response.PlatformBrandPageResponse;
import fu.osms.catalog.dto.response.PlatformCategorySuggestionResponse;
import fu.osms.catalog.service.PlatformLookupService;
import fu.osms.catalog.service.CategorySuggestionInputService;
import fu.osms.catalog.service.ProductChannelConfigService;
import fu.osms.catalog.service.impl.PlatformLookupServiceFactory;
import fu.osms.common.dto.ApiResponse;
import fu.osms.common.enums.PlatformType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ProductChannelConfigController {

    private final ProductChannelConfigService productChannelConfigService;
    private final CategorySuggestionInputService categorySuggestionInputService;
    private final PlatformLookupServiceFactory platformLookupServiceFactory;

    @GetMapping("/api/products/{productId}/channels/{channelId}/config")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES')")
    public ResponseEntity<ApiResponse<ChannelProductConfigResponse>> getConfig(
            @PathVariable UUID productId,
            @PathVariable UUID channelId) {
        return ResponseEntity.ok(ApiResponse.success(productChannelConfigService.getConfig(productId, channelId)));
    }

    @PutMapping("/api/products/{productId}/channels/{channelId}/config")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES')")
    public ResponseEntity<ApiResponse<ChannelProductConfigResponse>> updateConfig(
            @PathVariable UUID productId,
            @PathVariable UUID channelId,
            @RequestBody ChannelConfigRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Platform configuration updated",
                productChannelConfigService.updateConfig(productId, channelId, request)
        ));
    }

    @GetMapping("/api/platform-lookups/{platform}/categories")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES')")
    // MANUAL_CATEGORY_BROWSER_FALLBACK: kept while suggestion-first is verified.
    public ResponseEntity<ApiResponse<List<PlatformCategoryNodeResponse>>> categories(
            @PathVariable PlatformType platform,
            @RequestParam UUID channelId,
            @RequestParam(required = false) String parentId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String categoryVersion) {
        PlatformLookupService lookup = platformLookupServiceFactory.get(platform);
        return ResponseEntity.ok(ApiResponse.success(lookup.getCategories(channelId, parentId, keyword, categoryVersion)));
    }

    @GetMapping("/api/platform-lookups/{platform}/categories/{categoryId}/attributes")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES')")
    public ResponseEntity<ApiResponse<List<PlatformAttributeResponse>>> attributes(
            @PathVariable PlatformType platform,
            @PathVariable String categoryId,
            @RequestParam UUID channelId,
            @RequestParam(required = false) String categoryVersion) {
        PlatformLookupService lookup = platformLookupServiceFactory.get(platform);
        return ResponseEntity.ok(ApiResponse.success(lookup.getAttributes(channelId, categoryId, categoryVersion)));
    }

    @GetMapping("/api/platform-lookups/{platform}/brands")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES')")
    public ResponseEntity<ApiResponse<PlatformBrandPageResponse>> brands(
            @PathVariable PlatformType platform,
            @RequestParam UUID channelId,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String categoryVersion,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String pageToken) {
        return ResponseEntity.ok(ApiResponse.success(
                platformLookupServiceFactory.get(platform).getBrands(channelId, categoryId, categoryVersion, keyword,
                        Math.max(0, page), Math.min(Math.max(1, size), 100), pageToken)
        ));
    }

    @PostMapping("/api/platform-lookups/{platform}/category-suggestions")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES')")
    public ResponseEntity<ApiResponse<List<PlatformCategorySuggestionResponse>>> categorySuggestions(
            @PathVariable PlatformType platform,
            @RequestBody CategorySuggestionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                platformLookupServiceFactory.get(platform).suggestCategories(categorySuggestionInputService.resolve(request))
        ));
    }

    @PutMapping("/api/platform-lookups/{platform}/cache")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<Void>> clearCache(
            @PathVariable PlatformType platform,
            @RequestParam UUID channelId) {
        platformLookupServiceFactory.get(platform).clearCache(channelId);
        return ResponseEntity.ok(ApiResponse.success("Platform lookup cache cleared", null));
    }
}
