package fu.osms.auth.controller;

import fu.osms.auth.dto.request.ForgotPasswordRequest;
import fu.osms.auth.dto.request.LoginRequest;
import fu.osms.auth.dto.request.ChangePasswordRequest;
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

import java.util.Map;

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

        log.info("access={} ",tokenPairDTO.getAccessToken());

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
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@CookieValue String refreshToken) {
        return ResponseEntity.ok(ApiResponse.success(authService.refreshToken(refreshToken)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@CookieValue String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {

        try {
            authService.processForgotPassword(request.getEmail());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Vui lòng kiểm tra email của bạn"
            ));

        } catch (IllegalArgumentException ex) {

            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", ex.getMessage()
            ));
        }
    }
    @GetMapping("/change-password/validate")
    public ResponseEntity<?> validateToken(@RequestParam String token) {

        try {
            authService.validateResetToken(token);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Token hợp lệ"
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", ex.getMessage()
            ));
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        try {
            System.out.println(request.getPassword());
            System.out.println(request.getToken());

            System.out.println(request.getConfirmPassword());

            authService.updatePassword(request);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Cập nhật mật khẩu thành công. Vui lòng đăng nhập lại"
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", ex.getMessage()
            ));
        }
    }
}
