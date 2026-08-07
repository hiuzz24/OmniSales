package fu.osms.address.service;

import fu.osms.address.dto.CountryDto;
import fu.osms.address.entity.AdministrativeDivision;
import fu.osms.address.entity.Country;
import fu.osms.address.repository.AdministrativeDivisionRepository;
import fu.osms.address.repository.CountryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AddressServiceImpl Tests")
class AddressServiceImplTest {

    @Mock private CountryRepository countryRepository;
    @Mock private AdministrativeDivisionRepository divisionRepository;
    @Mock private RestCountriesService restCountriesService;

    private AddressServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AddressServiceImpl(countryRepository, divisionRepository, restCountriesService);
    }

    private AdministrativeDivision division(String countryCode) {
        return AdministrativeDivision.builder()
                .countryCode(countryCode)
                .code(countryCode + "-ROOT")
                .name("Root of " + countryCode)
                .level(1)
                .build();
    }

    @Test
    @DisplayName("getAllCountries: prefers API result, maps API 3-letter codes to DB 2-letter codes, marks hasDivisions")
    void getAllCountries_prefersApi() {
        when(restCountriesService.getAllCountries()).thenReturn(List.of(
                Country.builder().code("VNM").name("Vietnam").flagEmoji("VN").build(),
                Country.builder().code("USA").name("United States").flagEmoji("US").build(),
                Country.builder().code("XYZ").name("Unknown").build()
        ));
        when(divisionRepository.findAll()).thenReturn(List.of(division("VN")));

        List<CountryDto> result = service.getAllCountries();

        assertThat(result).hasSize(3);
        // sorted by name: "United States" < "Unknown" < "Vietnam"
        assertThat(result).extracting(CountryDto::getCode).containsExactly("US", "XYZ", "VN");
        CountryDto vn = result.stream().filter(c -> "VN".equals(c.getCode())).findFirst().orElseThrow();
        CountryDto us = result.stream().filter(c -> "US".equals(c.getCode())).findFirst().orElseThrow();
        assertThat(vn.isHasDivisions()).isTrue();
        assertThat(us.isHasDivisions()).isFalse();
    }

    @Test
    @DisplayName("getAllCountries: falls back to DB when API returns empty list")
    void getAllCountries_fallbackToDb() {
        when(restCountriesService.getAllCountries()).thenReturn(List.of());
        when(divisionRepository.findAll()).thenReturn(List.of(division("VN")));
        when(countryRepository.findAllByOrderByNameAsc()).thenReturn(List.of(
                Country.builder().code("VN").name("Vietnam").flagEmoji("VN").build()
        ));

        List<CountryDto> result = service.getAllCountries();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCode()).isEqualTo("VN");
        assertThat(result.get(0).isHasDivisions()).isTrue();
    }

    @Test
    @DisplayName("getAllCountries: handles null flagEmoji by defaulting to empty string")
    void getAllCountries_nullFlag() {
        when(restCountriesService.getAllCountries()).thenReturn(List.of(
                Country.builder().code("VNM").name("Vietnam").flagEmoji(null).build()
        ));
        when(divisionRepository.findAll()).thenReturn(List.of());

        List<CountryDto> result = service.getAllCountries();

        assertThat(result.get(0).getFlagEmoji()).isEmpty();
    }

    @Test
    @DisplayName("getDivisions: returns empty when countryCode is null or blank")
    void getDivisions_nullCountryCode() {
        assertThat(service.getDivisions(null, 1, null)).isEmpty();
        assertThat(service.getDivisions("", 1, null)).isEmpty();
    }

    @Test
    @DisplayName("getDivisions: returns empty when level is null or out of range")
    void getDivisions_invalidLevel() {
        assertThat(service.getDivisions("VN", null, null)).isEmpty();
        assertThat(service.getDivisions("VN", 0, null)).isEmpty();
        assertThat(service.getDivisions("VN", 4, null)).isEmpty();
    }

    @Test
    @DisplayName("getDivisions: level=1 returns root divisions (mapped to DB code)")
    void getDivisions_level1() {
        when(divisionRepository.findRootByCountryAndLevel("VN", 1)).thenReturn(List.of(
                AdministrativeDivision.builder().countryCode("VN").code("VN-01").name("Hanoi").level(1).build()
        ));

        List<AdministrativeDivision> result = service.getDivisions("VNM", 1, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Hanoi");
        // mapped from VNM (API) to VN (DB)
    }

    @Test
    @DisplayName("getDivisions: level>1 with parentCode returns children")
    void getDivisions_level2WithParent() {
        when(divisionRepository.findByCountryAndParent("VN", "VN-01")).thenReturn(List.of(
                AdministrativeDivision.builder().countryCode("VN").code("VN-01-A").name("District A").level(2).parentCode("VN-01").build()
        ));

        List<AdministrativeDivision> result = service.getDivisions("VN", 2, "VN-01");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCode()).isEqualTo("VN-01-A");
    }

    @Test
    @DisplayName("getDivisions: level>1 without parentCode returns empty")
    void getDivisions_level2NoParent() {
        List<AdministrativeDivision> result = service.getDivisions("VN", 2, null);

        assertThat(result).isEmpty();
    }
}
