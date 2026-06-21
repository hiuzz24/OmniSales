package fu.osms.auth.controller;

import fu.osms.auth.dto.request.*;
import fu.osms.auth.dto.response.AuthResponse;
import fu.osms.auth.dto.response.ResetPasswordResponse;
import fu.osms.auth.dto.response.TokenPairDTO;
import fu.osms.auth.security.JwtService;
import fu.osms.auth.service.AuthService;
import fu.osms.auth.service.CookieService;
import fu.osms.auth.service.UserService;
import fu.osms.common.dto.ApiResponse;
import fu.osms.common.exception.AppException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
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

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            Authentication authentication) {

        String email = authentication.getName();

        UUID requestUserId = userService.findUserIdByEmail(email);

        ResetPasswordResponse response =
                authService.resetUserPassword(
                        request,
                        requestUserId);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/changes-password-after-login")
    public ResponseEntity<?> changePasswordAfterLogin(
            @Valid @RequestBody ChangePasswordAfterLoginRequest request,
            Authentication authentication) {

        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(401).body(Map.of(
                        "success", false,
                        "message", "Phiên làm việc không hợp lệ hoặc đã hết hạn"
                ));
            }

            String email = authentication.getName();

            UUID userId = userService.findUserIdByEmail(email);

            authService.changePasswordAfterLogin(
                    userId,
                    request.getOldPassword(),
                    request.getNewPassword(),
                    request.getConfirmPassword()
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Đổi mật khẩu thành công. Vui lòng sử dụng mật khẩu mới cho lần đăng nhập sau."
            ));

        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", ex.getMessage()
            ));
        }
    }
}
