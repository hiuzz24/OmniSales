package fu.osms.auth.controller;

import fu.osms.auth.dto.request.*;
import fu.osms.auth.dto.response.AuthResponse;
import fu.osms.auth.dto.response.ResetPasswordResponse;
import fu.osms.auth.dto.response.TokenPairDTO;
import fu.osms.auth.entity.UserInviteToken;
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
    /** Xác thực người dùng và lưu refresh token mới trong cookie HTTP-only. */
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
    /** Luân chuyển refresh token hợp lệ và trả về cặp access token mới. */
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
    /** Thu hồi refresh token hiện tại và xóa cookie khỏi trình duyệt. */
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
    /** Khởi tạo luồng đặt lại mật khẩu mà không tiết lộ email có tồn tại hay không. */
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
    @PostMapping("/invite-user")
    /** Tạo lời mời người dùng và gán vai trò được yêu cầu. */
    public ResponseEntity<?> InviteUser(
            @Valid @RequestBody InviteUserRequest inviteUserRequest) {

        try {
            authService.processInviteUser(inviteUserRequest.getEmail(), inviteUserRequest.getRoleName());

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

    @GetMapping("/accept-invite/validate")
    /** Kiểm tra token lời mời tồn tại, còn hiệu lực và chưa hết hạn. */
    public ResponseEntity<?> validateInviteToken(@RequestParam String token) {
        try {
            UserInviteToken inviteToken = authService.validateInviteToken(token);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", Map.of(
                            "email", inviteToken.getEmail(),
                            "roleName", inviteToken.getRoleName()
                    )
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", ex.getMessage()
            ));
        }
    }

    @PostMapping("/accept-invite")
    /** Kích hoạt tài khoản được mời sau khi người dùng đặt mật khẩu hợp lệ. */
    public ResponseEntity<?> acceptInvite(@Valid @RequestBody AcceptInviteRequest request) {
        try {
            authService.acceptInvite(request);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Kích hoạt tài khoản thành công"
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", ex.getMessage()
            ));
        }
    }

    @GetMapping("/change-password/validate")
    /** Xác thực token đặt lại mật khẩu trước khi gửi biểu mẫu. */
    public ResponseEntity<?> validateToken(@RequestParam String token) {

        try {
            authService.validateToken(token);
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
    /** Đổi mật khẩu thông qua luồng đặt lại bằng token. */
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
    /** Cho phép quản trị viên có quyền đặt lại mật khẩu của người dùng khác. */
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
    /** Đổi mật khẩu người dùng hiện tại sau khi xác minh mật khẩu cũ. */
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
