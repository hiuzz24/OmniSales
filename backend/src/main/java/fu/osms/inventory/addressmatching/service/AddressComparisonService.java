package fu.osms.inventory.addressmatching.service;

import fu.osms.inventory.addressmatching.AddressMatchResult;
import fu.osms.inventory.addressmatching.dto.response.AddressGroupResponse;

import java.util.List;

public interface AddressComparisonService {

    AddressMatchResult compare(String address1, String address2);

    AddressGroupResponse groupAddresses(List<String> addresses);
}
