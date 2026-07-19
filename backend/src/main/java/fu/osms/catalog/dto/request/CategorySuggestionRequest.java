package fu.osms.catalog.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CategorySuggestionRequest {
    private UUID channelId;
    private UUID productId;
    private String title;
    private String description;
    private String primaryImageUrl;
    private String categoryVersion;
}
