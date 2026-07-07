package fu.osms.auth.service.impl;

import fu.osms.audit.repository.AuditLogRepository;
import fu.osms.auth.dto.request.LoginRequest;
import fu.osms.auth.dto.response.TokenPairDTO;
import fu.osms.auth.dto.response.UserResponse;
import fu.osms.auth.entity.*;
import fu.osms.auth.enums.UserStatus;
import fu.osms.auth.mapper.UserMapper;
import fu.osms.auth.repository.*;
import fu.osms.auth.security.JwtService;
import fu.osms.auth.service.AuthService;
import fu.osms.auth.service.EmailService;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.config.CustomUserDetailService;
import fu.osms.system.service.SystemSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private UserRepository userRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private CustomUserDetailService userDetailsService;
    @Mock private JwtService jwtService;
    @Mock private UserMapper userMapper;
    @Mock private PasswordResetTokenRepository tokenRepository;
    @Mock private EmailService emailService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserInviteTokenRepository userInviteTokenRepository;
    @Mock private SystemSettingService systemSettingService;
    private AuthServiceImpl authService;

    private User testUser;
    private LoginRequest loginRequest;

    private void injectValue(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception ignored) {}
    }

    private void injectFields() {
        injectValue(authService, "maxFailedAttempts", 5);
        injectValue(authService, "lockTimeDuration", 5);
    }

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                authenticationManager, userRepository, userRoleRepository,
                refreshTokenRepository, userDetailsService, jwtService,
                userMapper, tokenRepository, emailService, passwordEncoder,
                auditLogRepository, roleRepository, userInviteTokenRepository, systemSettingService);
        injectFields();

        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .passwordHash("hashedPassword")
                .fullName("Test User")
                .status(UserStatus.ACTIVE)
                .failedLoginAttempts(0)
                .passwordExpired(false)
                .build();

        loginRequest = LoginRequest.builder()
                .email("test@example.com")
                .password("Password123@")
                .build();
    }

    @Nested
    @DisplayName("Login Tests")
    class LoginTests {

        @Test
        @DisplayName("Should login successfully with valid credentials")
        void login_Success() {
            Role role = Role.builder().id(UUID.randomUUID()).name("ADMIN").build();
            UserRole userRole = UserRole.builder().id(UUID.randomUUID()).user(testUser).role(role).build();
            UserResponse userResponse = UserResponse.builder()
                    .id(testUser.getId())
                    .email(testUser.getEmail())
                    .fullName(testUser.getFullName())
                    .role("ADMIN")
                    .build();

            when(userRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.of(testUser));
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(null);
            when(userRoleRepository.findByUserId(testUser.getId())).thenReturn(List.of(userRole));
            when(userMapper.toResponse(testUser)).thenReturn(userResponse);
            when(userDetailsService.loadUserByUsername(testUser.getEmail()))
                    .thenReturn(new org.springframework.security.core.userdetails.User(
                            testUser.getEmail(), testUser.getPasswordHash(),
                            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            when(jwtService.generateAccessToken(any())).thenReturn("access-token");
            when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token");
            when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

            TokenPairDTO result = authService.login(loginRequest);

            assertThat(result).isNotNull();
            assertThat(result.getAccessToken()).isEqualTo("access-token");
            assertThat(result.getRefreshToken()).isEqualTo("refresh-token");
            assertThat(result.getUser().getEmail()).isEqualTo("test@example.com");
            assertThat(testUser.getFailedLoginAttempts()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should throw exception when email not found")
        void login_EmailNotFound() {
            when(userRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(loginRequest))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        }

        @Test
        @DisplayName("Should throw exception when account is locked")
        void login_AccountLocked() {
            testUser.setLockedUntil(OffsetDateTime.now().plusMinutes(5));

            when(userRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.of(testUser));

            assertThatThrownBy(() -> authService.login(loginRequest))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ACCOUNT_LOCKED);
        }

        @Test
        @DisplayName("Should unlock account when lock time has expired")
        void login_UnlockExpired() {
            testUser.setLockedUntil(OffsetDateTime.now().minusMinutes(1));

            Role role = Role.builder().id(UUID.randomUUID()).name("ADMIN").build();
            UserRole userRole = UserRole.builder().id(UUID.randomUUID()).user(testUser).role(role).build();
            UserResponse userResponse = UserResponse.builder().id(testUser.getId()).email(testUser.getEmail()).build();

            when(userRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(null);
            when(userRoleRepository.findByUserId(testUser.getId())).thenReturn(List.of(userRole));
            when(userMapper.toResponse(testUser)).thenReturn(userResponse);
            when(userDetailsService.loadUserByUsername(testUser.getEmail()))
                    .thenReturn(new org.springframework.security.core.userdetails.User(
                            testUser.getEmail(), testUser.getPasswordHash(),
                            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            when(jwtService.generateAccessToken(any())).thenReturn("access-token");
            when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token");
            when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

            TokenPairDTO result = authService.login(loginRequest);

            assertThat(result).isNotNull();
            verify(userRepository, times(1)).save(any(User.class));
        }

        @Test
        @DisplayName("Should increment failed login attempts and lock after max attempts")
        void login_MaxFailedAttempts_LocksAccount() {
            testUser.setFailedLoginAttempts(4);

            when(userRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.of(testUser));
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenThrow(new BadCredentialsException("Bad credentials"));
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

            assertThatThrownBy(() -> authService.login(loginRequest))
                    .isInstanceOf(AppException.class)
                    .satisfies(e -> {
                        AppException appEx = (AppException) e;
                        assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_LOCKED);
                        assertThat(appEx.getMessage()).contains("5 lần");
                    });

            assertThat(testUser.getStatus()).isEqualTo(UserStatus.LOCKED);
            assertThat(testUser.getFailedLoginAttempts()).isEqualTo(5);
            assertThat(testUser.getLockedUntil()).isNotNull();
        }

        @Test
        @DisplayName("Should show remaining attempts on failed login")
        void login_ShowRemainingAttempts() {
            testUser.setFailedLoginAttempts(2);

            when(userRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.of(testUser));
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenThrow(new BadCredentialsException("Bad credentials"));
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

            assertThatThrownBy(() -> authService.login(loginRequest))
                    .isInstanceOf(AppException.class)
                    .satisfies(e -> {
                        AppException appEx = (AppException) e;
                        assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
                        assertThat(appEx.getMessage()).contains("2 lần thử");
                    });
        }

        @Test
        @DisplayName("Should throw exception when user has no role")
        void login_NoRole() {
            when(userRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.of(testUser));
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(null);
            when(userRoleRepository.findByUserId(testUser.getId())).thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> authService.login(loginRequest))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("Refresh Token Tests")
    class RefreshTokenTests {

        @Test
        @DisplayName("Should refresh token successfully")
        void refreshToken_Success() {
            String refreshToken = "valid-refresh-token";
            RefreshToken storedToken = RefreshToken.builder()
                    .id(UUID.randomUUID())
                    .user(testUser)
                    .tokenHash("hashed")
                    .expiresAt(OffsetDateTime.now().plusDays(7))
                    .build();

            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(storedToken));
            when(userDetailsService.loadUserByUsername(testUser.getEmail()))
                    .thenReturn(new org.springframework.security.core.userdetails.User(
                            testUser.getEmail(), testUser.getPasswordHash(),
                            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            when(jwtService.generateAccessToken(any())).thenReturn("new-access-token");
            when(jwtService.generateRefreshToken(any())).thenReturn("new-refresh-token");
            when(userMapper.toResponse(testUser)).thenReturn(UserResponse.builder().email(testUser.getEmail()).build());
            when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

            TokenPairDTO result = authService.refreshToken(refreshToken);

            assertThat(result.getAccessToken()).isEqualTo("new-access-token");
            assertThat(result.getRefreshToken()).isEqualTo("new-refresh-token");
            assertThat(storedToken.getRevokedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should throw exception for invalid token")
        void refreshToken_InvalidToken() {
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refreshToken("invalid-token"))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.TOKEN_INVALID);
        }

        @Test
        @DisplayName("Should throw exception for revoked token")
        void refreshToken_RevokedToken() {
            RefreshToken revokedToken = RefreshToken.builder()
                    .id(UUID.randomUUID())
                    .user(testUser)
                    .revokedAt(OffsetDateTime.now().minusMinutes(5))
                    .expiresAt(OffsetDateTime.now().plusDays(7))
                    .build();

            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(revokedToken));

            assertThatThrownBy(() -> authService.refreshToken("revoked-token"))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.TOKEN_REVOKED);
        }

        @Test
        @DisplayName("Should throw exception for expired token")
        void refreshToken_ExpiredToken() {
            RefreshToken expiredToken = RefreshToken.builder()
                    .id(UUID.randomUUID())
                    .user(testUser)
                    .expiresAt(OffsetDateTime.now().minusMinutes(5))
                    .build();

            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(expiredToken));

            assertThatThrownBy(() -> authService.refreshToken("expired-token"))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.TOKEN_EXPIRED);
        }
    }

    @Nested
    @DisplayName("Logout Tests")
    class LogoutTests {

        @Test
        @DisplayName("Should logout successfully")
        void logout_Success() {
            String refreshToken = "valid-refresh-token";
            RefreshToken storedToken = RefreshToken.builder()
                    .id(UUID.randomUUID())
                    .user(testUser)
                    .build();

            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(storedToken));
            when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

            authService.logout(refreshToken);

            assertThat(storedToken.getRevokedAt()).isNotNull();
            verify(refreshTokenRepository).save(storedToken);
        }

        @Test
        @DisplayName("Should not throw when token not found during logout")
        void logout_TokenNotFound() {
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

            authService.logout("non-existent-token");

            verify(refreshTokenRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Password Validation Tests")
    class PasswordValidationTests {

        @Test
        @DisplayName("Should validate correct password format")
        void isValidPasswordFormat_Valid() {
            assertThat(authService.isValidPasswordFormat("Password123@")).isTrue();
            assertThat(authService.isValidPasswordFormat("Abcdefgh1!")).isTrue();
        }

        @Test
        @DisplayName("Should reject weak passwords")
        void isValidPasswordFormat_Weak() {
            assertThat(authService.isValidPasswordFormat("short")).isFalse();
            assertThat(authService.isValidPasswordFormat("nodigits!")).isFalse();
            assertThat(authService.isValidPasswordFormat("NoSpecial1")).isFalse();
            assertThat(authService.isValidPasswordFormat("noupcase1!")).isFalse();
            assertThat(authService.isValidPasswordFormat("NOLOWERCASE1!")).isFalse();
            assertThat(authService.isValidPasswordFormat(null)).isFalse();
        }
    }
}
