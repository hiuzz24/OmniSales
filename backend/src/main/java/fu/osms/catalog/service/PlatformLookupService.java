package fu.osms.catalog.service;

import fu.osms.catalog.dto.request.CategorySuggestionRequest;
import fu.osms.catalog.dto.response.PlatformAttributeResponse;
import fu.osms.catalog.dto.response.PlatformBrandResponse;
import fu.osms.catalog.dto.response.PlatformBrandPageResponse;
import fu.osms.catalog.dto.response.PlatformCategoryNodeResponse;
import fu.osms.catalog.dto.response.PlatformCategorySuggestionResponse;
import fu.osms.common.enums.PlatformType;

import java.util.List;
import java.util.UUID;

public interface PlatformLookupService {
    PlatformType getPlatform();

    List<PlatformCategoryNodeResponse> getCategories(UUID channelId, String parentId, String keyword, String categoryVersion);

    List<PlatformAttributeResponse> getAttributes(UUID channelId, String categoryId, String categoryVersion);

    default PlatformBrandPageResponse getBrands(UUID channelId, String categoryId, String categoryVersion, String keyword,
                                                int page, int size, String pageToken) {
        return PlatformBrandPageResponse.builder()
                .items(List.of()).page(page).size(size).totalElements(0).totalPages(0).build();
    }

    default List<PlatformCategorySuggestionResponse> suggestCategories(CategorySuggestionRequest request) {
        return List.of();
    }

    boolean isLeafCategory(UUID channelId, String categoryId, String categoryVersion);

    void clearCache(UUID channelId);
}
