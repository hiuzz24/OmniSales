package fu.osms.auth.controller;

import fu.osms.auth.dto.request.LoginRequest;
import fu.osms.auth.dto.response.AuthResponse;
import fu.osms.auth.dto.response.TokenPairDTO;
import fu.osms.auth.security.JwtService;
import fu.osms.auth.service.AuthService;
import fu.osms.auth.service.CookieService;
import fu.osms.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CookieService cookieService;
    private final JwtService jwtService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request,
                                                           HttpServletResponse response) {
        TokenPairDTO tokenPairDTO = authService.login(request);

        cookieService.addRefreshTokenCookie(tokenPairDTO.getRefreshToken(),response);

        AuthResponse authResponse = AuthResponse.builder()
                .accessToken(tokenPairDTO.getAccessToken())
                .expiresIn(jwtService.getAccessTokenExpirationMs() / 1000)
                .tokenType("Bearer")
                .user(tokenPairDTO.getUser())
                .build();

        return ResponseEntity.ok(ApiResponse.success(authResponse));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@CookieValue String refreshToken, HttpServletResponse response) {
        TokenPairDTO pair = authService.refreshToken(refreshToken);
        cookieService.addRefreshTokenCookie(pair.getRefreshToken(), response);

        AuthResponse authResponse = AuthResponse.builder()
                .accessToken(pair.getAccessToken())
                .expiresIn(jwtService.getAccessTokenExpirationMs() / 1000)
                .tokenType("Bearer")
                .user(pair.getUser())
                .build();

        return ResponseEntity.ok(ApiResponse.success(authResponse));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }
        cookieService.clearRefreshTokenCookie(response);
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
    }
}
