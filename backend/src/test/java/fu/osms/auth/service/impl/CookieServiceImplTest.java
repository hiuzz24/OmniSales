package fu.osms.auth.service.impl;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("CookieServiceImpl Tests")
class CookieServiceImplTest {

    private final CookieServiceImpl cookieService = new CookieServiceImpl();

    @Test
    @DisplayName("addRefreshTokenCookie sets refreshToken with httpOnly, secure, sameSite=Strict, path=/api/auth, maxAge=7 days")
    void addRefreshTokenCookie_setsExpectedAttributes() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        String token = "rt-abc-123";

        cookieService.addRefreshTokenCookie(token, response);

        String setCookie = response.getHeader("Set-Cookie");
        assertThat(setCookie).isNotNull();
        assertThat(setCookie)
                .contains("refreshToken=" + token)
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Strict")
                .contains("Path=/api/auth")
                .contains("Max-Age=604800"); // 7 days in seconds
    }

    @Test
    @DisplayName("addRefreshTokenCookie emits exactly one Set-Cookie header")
    void addRefreshTokenCookie_addsSingleHeader() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookieService.addRefreshTokenCookie("token-x", response);

        java.util.Collection<String> setCookieValues = response.getHeaders("Set-Cookie");
        assertThat(setCookieValues).hasSize(1);
    }

    @Test
    @DisplayName("clearRefreshTokenCookie overrides refreshToken at /api/auth and clears the legacy /api/auth/refresh path")
    void clearRefreshTokenCookie_clearsBothPaths() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookieService.clearRefreshTokenCookie(response);

        // ResponseCookie headers are stored as comma-separated in MockHttpServletResponse,
        // so we collect every value associated with the Set-Cookie name.
        var headers = response.getHeaders("Set-Cookie");
        assertThat(headers).hasSize(2);

        String combined = String.join(",", headers);
        assertThat(combined)
                .contains("refreshToken=")
                .contains("Path=/api/auth")
                .contains("Path=/api/auth/refresh")
                .contains("Max-Age=0");
    }

    @Test
    @DisplayName("addRefreshTokenCookie respects the supplied token verbatim")
    void addRefreshTokenCookie_preservesToken() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        ArgumentCaptor<String> headerCaptor = ArgumentCaptor.forClass(String.class);

        // Easier path: just check the raw header.
        cookieService.addRefreshTokenCookie("opaque-token-value", response);

        String header = response.getHeader("Set-Cookie");
        assertThat(header).contains("opaque-token-value");
    }
}
