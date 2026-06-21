package fu.osms.address.service;

import fu.osms.address.dto.CountryDto;
import fu.osms.address.entity.AdministrativeDivision;

import java.util.List;

public interface AddressService {

    List<CountryDto> getAllCountries();

    List<AdministrativeDivision> getDivisions(String countryCode, Integer level, String parentCode);
}
