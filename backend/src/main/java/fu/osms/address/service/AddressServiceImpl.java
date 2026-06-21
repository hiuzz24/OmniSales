package fu.osms.address.service;

import fu.osms.address.dto.CountryDto;
import fu.osms.address.entity.AdministrativeDivision;
import fu.osms.address.entity.Country;
import fu.osms.address.repository.AdministrativeDivisionRepository;
import fu.osms.address.repository.CountryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AddressServiceImpl implements AddressService {

    private final CountryRepository countryRepository;
    private final AdministrativeDivisionRepository divisionRepository;
    private final RestCountriesService restCountriesService;

    // REST Countries API uses 3-letter codes; DB uses 2-letter codes.
    private static final Map<String, String> CODE_MAP = Map.ofEntries(
            Map.entry("VNM", "VN"),
            Map.entry("USA", "US"),
            Map.entry("CHN", "CN"),
            Map.entry("GBR", "GB"),
            Map.entry("KOR", "KR"),
            Map.entry("SGP", "SG"),
            Map.entry("THA", "TH"),
            Map.entry("MYS", "MY"),
            Map.entry("AUS", "AU"),
            Map.entry("CAN", "CA"),
            Map.entry("IND", "IN"),
            Map.entry("RUS", "RU"),
            Map.entry("BRA", "BR")
    );

    private String mapToDbCode(String apiCode) {
        if (apiCode == null) return null;
        return CODE_MAP.getOrDefault(apiCode, apiCode);
    }

    private String mapToApiCode(String dbCode) {
        if (dbCode == null) return null;
        for (Map.Entry<String, String> e : CODE_MAP.entrySet()) {
            if (e.getValue().equals(dbCode)) return e.getKey();
        }
        return dbCode;
    }

    private Set<String> getAvailableCountryCodes() {
        return divisionRepository.findAll().stream()
                .map(AdministrativeDivision::getCountryCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    @Override
    public List<CountryDto> getAllCountries() {
        List<Country> fromApi = restCountriesService.getAllCountries();
        Set<String> available = getAvailableCountryCodes();

        List<CountryDto> result;
        if (fromApi == null || fromApi.isEmpty()) {
            List<Country> dbCountries = countryRepository.findAllByOrderByNameAsc();
            result = dbCountries.stream()
                    .map(c -> CountryDto.builder()
                            .code(c.getCode())
                            .name(c.getName())
                            .flagEmoji(c.getFlagEmoji() != null ? c.getFlagEmoji() : "")
                            .hasDivisions(available.contains(c.getCode()))
                            .build())
                    .collect(Collectors.toList());
        } else {
            result = fromApi.stream()
                    .map(c -> {
                        String dbCode = mapToDbCode(c.getCode());
                        return CountryDto.builder()
                                .code(dbCode)
                                .name(c.getName())
                                .flagEmoji(c.getFlagEmoji() != null ? c.getFlagEmoji() : "")
                                .hasDivisions(available.contains(dbCode))
                                .build();
                    })
                    .collect(Collectors.toList());
        }

        result.sort(Comparator.comparing(CountryDto::getName));
        return result;
    }

    @Override
    public List<AdministrativeDivision> getDivisions(String countryCode, Integer level, String parentCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return Collections.emptyList();
        }

        if (level == null || level < 1 || level > 3) {
            return Collections.emptyList();
        }

        String dbCountryCode = mapToDbCode(countryCode);

        if (level == 1) {
            return divisionRepository.findRootByCountryAndLevel(dbCountryCode, level);
        }

        if (parentCode != null && !parentCode.isBlank()) {
            return divisionRepository.findByCountryAndParent(dbCountryCode, parentCode);
        }

        return Collections.emptyList();
    }
}
