package fu.osms.it;

import fu.osms.address.entity.Country;
import fu.osms.address.service.RestCountriesService;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;

/**
 * Provides mock beans for all external integrations so that
 * {@code @SpringBootTest} integration tests can boot without
 * hitting Gmail SMTP, RestCountries API, RabbitMQ, etc.
 *
 * <p>Registered automatically through the {@code @Import} in
 * {@link IntegrationTestBase}.</p>
 */
@TestConfiguration
public class TestExternalServicesConfig {

    /** Mocks Gmail SMTP — all {@code send()} calls become no-ops. */
    @Bean
    @Primary
    public JavaMailSender javaMailSender() {
        return Mockito.mock(JavaMailSender.class, Mockito.RETURNS_DEEP_STUBS);
    }

    /** Prevents RestCountries API calls from failing. */
    @Bean
    @Primary
    public RestCountriesService restCountriesService() {
        return () -> List.of(
                Country.builder().code("VN").name("Vietnam").build(),
                Country.builder().code("US").name("United States").build());
    }

    /** Prevents RabbitMQ from needing to be running. */
    @Bean
    @Primary
    public org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory() {
        return new org.springframework.amqp.rabbit.connection.CachingConnectionFactory();
    }
}
