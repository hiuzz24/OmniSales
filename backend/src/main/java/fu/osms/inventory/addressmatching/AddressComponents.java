package fu.osms.inventory.addressmatching;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressComponents {
    private String houseNumber;
    private String street;
    private String ward;
    private String district;
    private String city;
    private String country;
    private String rawAddress;
}
