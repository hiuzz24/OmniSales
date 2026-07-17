package fu.osms.catalog.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class PlatformCategorySuggestionResponse {
    private final String inputHash;
    private final String categoryId;
    private final String categoryName;
    private final boolean selectable;
    private final String disabledReason;
}
