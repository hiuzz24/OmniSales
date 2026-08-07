package fu.osms.address.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.address.entity.AdministrativeDivision;
import fu.osms.address.repository.AdministrativeDivisionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class AddressDataInitializer implements CommandLineRunner {

    private final AdministrativeDivisionRepository divisionRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void run(String... args) throws Exception {
        boolean hasAny = divisionRepository.count() > 0;
        long vnLevel3Count = hasAny ? divisionRepository.countByCountryCodeAndLevel("VN", 3) : 0L;

        if (hasAny && vnLevel3Count > 0) {
            log.info("Address data already exists (including VN level 3), skipping seed.");
            return;
        }

        if (hasAny) {
            log.info("Existing address data found but no VN level-3 entries. Running partial seed for level 3 only...");
            seedLevel3Only();
            return;
        }

        log.info("Seeding address data from JSON...");
        long start = System.currentTimeMillis();

        JsonNode root;
        try (InputStream is = new ClassPathResource("address-data.json").getInputStream()) {
            root = objectMapper.readTree(is);
        }

        List<AdministrativeDivision> divisions = new ArrayList<>();
        JsonNode divsNode = root.get("divisions").get("VN");
        if (divsNode != null) {
            for (JsonNode d : divsNode) {
                AdministrativeDivision div = AdministrativeDivision.builder()
                        .countryCode("VN")
                        .code(d.get("code").asText())
                        .name(d.get("name").asText())
                        .level(d.get("level").asInt())
                        .parentCode(d.has("parentCode") && !d.get("parentCode").isNull()
                                ? d.get("parentCode").asText() : null)
                        .build();
                divisions.add(div);
            }
        }

        addCountryDivisions(divisions, "US", List.of(
                new String[]{"US|CA","California"}, new String[]{"US|NY","New York"},
                new String[]{"US|TX","Texas"}, new String[]{"US|FL","Florida"},
                new String[]{"US|WA","Washington"}, new String[]{"US|IL","Illinois"},
                new String[]{"US|PA","Pennsylvania"}, new String[]{"US|OH","Ohio"},
                new String[]{"US|GA","Georgia"}, new String[]{"US|NC","North Carolina"}
        ));
        addCountryDivisions(divisions, "JP", List.of(
                new String[]{"JP|13","Tokyo"}, new String[]{"JP|14","Kanagawa"},
                new String[]{"JP|27","Osaka"}, new String[]{"JP|28","Kyoto"},
                new String[]{"JP|01","Hokkaido"}, new String[]{"JP|22","Aichi"},
                new String[]{"JP|40","Fukuoka"}, new String[]{"JP|34","Hiroshima"}
        ));
        addCountryDivisions(divisions, "GB", List.of(
                new String[]{"GB|ENG","England"}, new String[]{"GB|SCT","Scotland"},
                new String[]{"GB|WLS","Wales"}, new String[]{"GB|NIR","Northern Ireland"}
        ));
        addCountryDivisions(divisions, "DE", List.of(
                new String[]{"DE|BY","Bayern"}, new String[]{"DE|NW","Nordrhein-Westfalen"},
                new String[]{"DE|BW","Baden-Württemberg"}, new String[]{"DE|SN","Sachsen"},
                new String[]{"DE|BE","Berlin"}, new String[]{"DE|HH","Hamburg"}
        ));
        addCountryDivisions(divisions, "FR", List.of(
                new String[]{"FR|75","Île-de-France"}, new String[]{"FR|13","Provence-Alpes-Côte d'Azur"},
                new String[]{"FR|59","Hauts-de-France"}, new String[]{"FR|33","Nouvelle-Aquitaine"},
                new String[]{"FR|69","Auvergne-Rhône-Alpes"}
        ));
        addCountryDivisions(divisions, "CN", List.of(
                new String[]{"CN|BJ","Beijing"}, new String[]{"CN|SH","Shanghai"},
                new String[]{"CN|GD","Guangdong"}, new String[]{"CN|ZJ","Zhejiang"},
                new String[]{"CN|JS","Jiangsu"}, new String[]{"CN|SC","Sichuan"}
        ));
        addCountryDivisions(divisions, "KR", List.of(
                new String[]{"KR|11","Seoul"}, new String[]{"KR|26","Busan"},
                new String[]{"KR|27","Daegu"}, new String[]{"KR|28","Daejeon"},
                new String[]{"KR|29","Gwangju"}, new String[]{"KR|30","Ulsan"},
                new String[]{"KR|41","Gyeonggi-do"}, new String[]{"KR|47","Gyeongsangbuk-do"}
        ));
        addCountryDivisions(divisions, "SG", List.of(
                new String[]{"SG|01","Central Singapore"}, new String[]{"SG|02","North East"},
                new String[]{"SG|03","North West"}, new String[]{"SG|04","South East"},
                new String[]{"SG|05","South West"}
        ));
        addCountryDivisions(divisions, "TH", List.of(
                new String[]{"TH|10","Bangkok"}, new String[]{"TH|80","Chiang Mai"},
                new String[]{"TH|20","Phuket"}, new String[]{"TH|50","Pattaya"}
        ));
        addCountryDivisions(divisions, "MY", List.of(
                new String[]{"MY|14","Kuala Lumpur"}, new String[]{"MY|15","Putrajaya"},
                new String[]{"MY|01","Johor"}, new String[]{"MY|02","Kedah"},
                new String[]{"MY|05","Melaka"}, new String[]{"MY|08","Selangor"}
        ));
        addCountryDivisions(divisions, "AU", List.of(
                new String[]{"AU|NSW","New South Wales"}, new String[]{"AU|VIC","Victoria"},
                new String[]{"AU|QLD","Queensland"}, new String[]{"AU|SA","South Australia"},
                new String[]{"AU|WA","Western Australia"}, new String[]{"AU|ACT","Australian Capital Territory"}
        ));
        addCountryDivisions(divisions, "CA", List.of(
                new String[]{"CA|ON","Ontario"}, new String[]{"CA|QC","Quebec"},
                new String[]{"CA|BC","British Columbia"}, new String[]{"CA|AB","Alberta"},
                new String[]{"CA|MB","Manitoba"}, new String[]{"CA|NS","Nova Scotia"}
        ));
        addCountryDivisions(divisions, "IN", List.of(
                new String[]{"IN|DL","Delhi"}, new String[]{"IN|MH","Maharashtra"},
                new String[]{"IN|TN","Tamil Nadu"}, new String[]{"IN|KA","Karnataka"},
                new String[]{"IN|GJ","Gujarat"}, new String[]{"IN|WB","West Bengal"}
        ));
        addCountryDivisions(divisions, "RU", List.of(
                new String[]{"RU|MOW","Moscow"}, new String[]{"RU|SPE","Saint Petersburg"},
                new String[]{"RU|KDA","Krasnodar Krai"}, new String[]{"RU|MOS","Moscow Oblast"},
                new String[]{"RU|SVE","Sverdlovsk Oblast"}
        ));
        addCountryDivisions(divisions, "BR", List.of(
                new String[]{"BR|SP","São Paulo"}, new String[]{"BR|RJ","Rio de Janeiro"},
                new String[]{"BR|MG","Minas Gerais"}, new String[]{"BR|BA","Bahia"},
                new String[]{"BR|DF","Distrito Federal"}
        ));

        int batchSize = 500;
        for (int i = 0; i < divisions.size(); i += batchSize) {
            List<AdministrativeDivision> batch = divisions.subList(i, Math.min(i + batchSize, divisions.size()));
            divisionRepository.saveAll(batch);
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("Seeded {} divisions in {} ms.", divisions.size(), elapsed);
    }

    private void seedLevel3Only() throws Exception {
        long start = System.currentTimeMillis();
        JsonNode root;
        try (InputStream is = new ClassPathResource("address-data.json").getInputStream()) {
            root = objectMapper.readTree(is);
        }

        List<AdministrativeDivision> level3 = new ArrayList<>();
        JsonNode divsNode = root.get("divisions").get("VN");
        if (divsNode != null) {
            for (JsonNode d : divsNode) {
                if (d.get("level").asInt() == 3) {
                    level3.add(AdministrativeDivision.builder()
                            .countryCode("VN")
                            .code(d.get("code").asText())
                            .name(d.get("name").asText())
                            .level(3)
                            .parentCode(d.get("parentCode").asText())
                            .build());
                }
            }
        }

        int batchSize = 500;
        for (int i = 0; i < level3.size(); i += batchSize) {
            List<AdministrativeDivision> batch = level3.subList(i, Math.min(i + batchSize, level3.size()));
            divisionRepository.saveAll(batch);
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("Seeded {} level-3 VN divisions in {} ms.", level3.size(), elapsed);
    }

    private void addCountryDivisions(List<AdministrativeDivision> list, String countryCode, List<String[]> entries) {
        for (String[] entry : entries) {
            list.add(AdministrativeDivision.builder()
                    .countryCode(countryCode)
                    .code(entry[0])
                    .name(entry[1])
                    .level(1)
                    .parentCode(null)
                    .build());
        }
    }
}
