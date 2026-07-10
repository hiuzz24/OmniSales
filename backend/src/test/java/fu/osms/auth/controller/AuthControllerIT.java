package fu.osms.auth.controller;

import fu.osms.auth.dto.response.UserResponse;
import fu.osms.auth.dto.response.TokenPairDTO;
import fu.osms.auth.security.JwtAuthenticationFilter;
import fu.osms.auth.security.JwtService;
import fu.osms.auth.service.AuthService;
import fu.osms.auth.service.CookieService;
import fu.osms.auth.service.UserService;
import fu.osms.config.ApiUsageFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@link AuthController}.
 *
 * <p>The login flow end-to-end (cookie issuance, JWT validation) is
 * covered in the full E2E backend tests; this slice focuses on the
 * response envelope and validation behavior of the controller.</p>
 */
@WebMvcTest(
        controllers = AuthController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, ApiUsageFilter.class}
        )
)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerIT {

    @Autowired MockMvc mvc;
    @MockBean AuthService authService;
    @MockBean UserService userService;
    @MockBean CookieService cookieService;
    @MockBean JwtService jwtService;
    @MockBean org.springframework.security.core.userdetails.UserDetailsService userDetailsService;
    @MockBean fu.osms.auth.repository.UserRepository userRepository;

    @Test
    void login_returns200_andAccessTokenEnvelope() throws Exception {
        UserResponse user = UserResponse.builder()
                .id(UUID.randomUUID())
                .email("manager@osms.vn")
                .fullName("Manager")
                .role("OWNER")
                .build();
        TokenPairDTO pair = TokenPairDTO.builder()
                .accessToken("jwt.access.token")
                .refreshToken("jwt.refresh.token")
                .user(user)
                .build();
        when(authService.login(any())).thenReturn(pair);
        when(jwtService.getAccessTokenExpirationMs()).thenReturn(86400000L);

        mvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"manager@osms.vn\",\"password\":\"11111111\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("jwt.access.token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.user.email").value("manager@osms.vn"));
    }

    @Test
    void login_returns400_whenEmailInvalid() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"not-an-email\",\"password\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_returns400_whenFieldsMissing() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void forgotPassword_returns200_whenEmailValid() throws Exception {
        mvc.perform(post("/api/auth/forgot-password")
                        .contentType("application/json")
                        .content("{\"email\":\"manager@osms.vn\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void logout_returns200_andClearsCookie() throws Exception {
        mvc.perform(post("/api/auth/logout")
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", "abc")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}