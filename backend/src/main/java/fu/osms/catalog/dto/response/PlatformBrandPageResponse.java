package fu.osms.catalog.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PlatformBrandPageResponse {
    private final List<PlatformBrandResponse> items;
    private final int page;
    private final int size;
    private final int totalElements;
    private final int totalPages;
    private final String nextPageToken;
    private final boolean hasNext;
}
