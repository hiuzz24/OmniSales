package fu.osms.it;

import fu.osms.auth.security.JwtAuthenticationFilter;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for controller-slice integration tests.
 *
 * <p>Boots only the web layer with {@code @WebMvcTest} (no real DB, no real
 * security filter) and excludes external service mocks via type filter.
 * Service-layer beans are replaced with {@code @MockBean} on the concrete
 * test class.</p>
 *
 * <p>For full-stack tests that need the entire Spring context (real JWT,
 * real DB, real filter chain) extend {@link BaseFullStackIT} instead.</p>
 */
@ActiveProfiles("it")
@WebMvcTest(excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = { JwtAuthenticationFilter.class }
))
@AutoConfigureMockMvc(addFilters = false)
public abstract class ControllerSliceITBase {
}
