package fu.osms.address.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CountryDto {
    private String code;
    private String name;
    private String flagEmoji;
    private boolean hasDivisions;
}
