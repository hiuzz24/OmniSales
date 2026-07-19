package fu.osms.catalog.dto.response;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class PlatformAttributeResponse {
    private String id;
    private String name;
    private String label;
    private String inputType;
    private Boolean required;
    private Boolean saleProperty;
    private List<PlatformAttributeOptionResponse> options;
}
