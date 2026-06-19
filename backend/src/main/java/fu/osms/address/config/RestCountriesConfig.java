package fu.osms.address.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestCountriesConfig {

    @Bean
    public RestTemplate restCountriesRestTemplate() {
        return new RestTemplate();
    }
}
