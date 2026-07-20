package fu.osms.auth.service.impl;

import fu.osms.audit.entity.AuditLog;
import fu.osms.audit.repository.AuditLogRepository;
import fu.osms.auth.dto.request.AcceptInviteRequest;
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
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
            when(systemSettingService.getInteger("max_failed_login_attempts", 5)).thenReturn(5);

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
            when(systemSettingService.getInteger("max_failed_login_attempts", 5)).thenReturn(5);

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

    @Nested
    @DisplayName("Invite User Tests")
    @org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    class InviteTests {

        private final String newEmail = "new-staff@osms.vn";

        private UserInviteToken pendingToken() {
            return UserInviteToken.builder()
                    .id(UUID.randomUUID())
                    .email(newEmail)
                    .roleName("SALES")
                    .token(UUID.randomUUID().toString())
                    .expiresAt(OffsetDateTime.now().plusMinutes(15))
                    .status("PENDING")
                    .build();
        }

        private Role roleSales() {
            return Role.builder().id(UUID.randomUUID()).name("SALES").build();
        }

        @Test
        @DisplayName("processInviteUser — happy path: new email + SALES → creates INACTIVE user + PENDING token + sends email")
        void processInviteUser_happy_newEmail_sales() {
            when(userInviteTokenRepository.findByEmailIgnoreCase(newEmail)).thenReturn(List.of());
            when(userRepository.findByEmail(newEmail)).thenReturn(Optional.empty());
            when(roleRepository.findByName("SALES")).thenReturn(Optional.of(roleSales()));
            when(passwordEncoder.encode(anyString())).thenReturn("encoded-random-uuid");
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
            when(userRoleRepository.findByUserId(any(UUID.class))).thenReturn(List.of());
            when(userRoleRepository.save(any(UserRole.class))).thenAnswer(i -> i.getArgument(0));
            when(userInviteTokenRepository.save(any(UserInviteToken.class))).thenAnswer(i -> i.getArgument(0));

            authService.processInviteUser(newEmail, "SALES");

            ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCap.capture());
            User savedUser = userCap.getValue();
            assertThat(savedUser.getEmail()).isEqualTo(newEmail);
            assertThat(savedUser.getStatus()).isEqualTo(UserStatus.INACTIVE);
            assertThat(savedUser.getFullName()).isEqualTo("Chờ kích hoạt");
            assertThat(savedUser.getPasswordHash()).isNotBlank();

            ArgumentCaptor<UserInviteToken> tokenCap = ArgumentCaptor.forClass(UserInviteToken.class);
            verify(userInviteTokenRepository).save(tokenCap.capture());
            UserInviteToken savedToken = tokenCap.getValue();
            assertThat(savedToken.getEmail()).isEqualTo(newEmail);
            assertThat(savedToken.getRoleName()).isEqualTo("SALES");
            assertThat(savedToken.getStatus()).isEqualTo("PENDING");
            assertThat(savedToken.getExpiresAt()).isAfter(OffsetDateTime.now());

            verify(emailService).sendInviteEmail(eq(newEmail), anyString());
        }

        @Test
        @DisplayName("processInviteUser — normalizes 'SALES STAFF' / 'OPERATIONS STAFF' to canonical roles")
        void processInviteUser_normalizesRoleNames() {
            when(userInviteTokenRepository.findByEmailIgnoreCase("ops@osms.vn")).thenReturn(List.of());
            when(userRepository.findByEmail("ops@osms.vn")).thenReturn(Optional.empty());
            when(roleRepository.findByName("OPERATIONS")).thenReturn(Optional.of(Role.builder().id(UUID.randomUUID()).name("OPERATIONS").build()));
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
            when(userRoleRepository.findByUserId(any(UUID.class))).thenReturn(List.of());
            when(userRoleRepository.save(any(UserRole.class))).thenAnswer(i -> i.getArgument(0));
            when(userInviteTokenRepository.save(any(UserInviteToken.class))).thenAnswer(i -> i.getArgument(0));

            authService.processInviteUser("ops@osms.vn", "  operations staff ");

            ArgumentCaptor<UserInviteToken> tokenCap = ArgumentCaptor.forClass(UserInviteToken.class);
            verify(userInviteTokenRepository).save(tokenCap.capture());
            assertThat(tokenCap.getValue().getRoleName()).isEqualTo("OPERATIONS");
        }

        @Test
        @DisplayName("processInviteUser — rejects OWNER role")
        void processInviteUser_rejectsOwner() {
            assertThatThrownBy(() -> authService.processInviteUser("x@y.vn", "OWNER"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Vai trò mời không hợp lệ");
        }

        @Test
        @DisplayName("processInviteUser — rejects ADMIN role")
        void processInviteUser_rejectsAdmin() {
            assertThatThrownBy(() -> authService.processInviteUser("x@y.vn", "ADMIN"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Vai trò mời không hợp lệ");
        }

        @Test
        @DisplayName("processInviteUser — rejects empty role")
        void processInviteUser_rejectsEmptyRole() {
            assertThatThrownBy(() -> authService.processInviteUser("x@y.vn", "  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Vai trò mời không hợp lệ");
        }

        @Test
        @DisplayName("processInviteUser — rejects when email has an ACCEPTED invite history")
        void processInviteUser_rejectsAcceptedHistory() {
            UserInviteToken accepted = UserInviteToken.builder()
                    .id(UUID.randomUUID())
                    .email(newEmail)
                    .roleName("SALES")
                    .token("prev-token")
                    .expiresAt(OffsetDateTime.now().plusMinutes(5))
                    .usedAt(OffsetDateTime.now().minusMinutes(2))
                    .status("ACCEPTED")
                    .build();
            when(userInviteTokenRepository.findByEmailIgnoreCase(newEmail)).thenReturn(List.of(accepted));

            assertThatThrownBy(() -> authService.processInviteUser(newEmail, "SALES"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("đã chấp nhận");
        }

        @Test
        @DisplayName("processInviteUser — rejects when there is a still-pending invite")
        void processInviteUser_rejectsPending() {
            when(userInviteTokenRepository.findByEmailIgnoreCase(newEmail)).thenReturn(List.of(pendingToken()));

            assertThatThrownBy(() -> authService.processInviteUser(newEmail, "SALES"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("đang có một lời mời chưa xác nhận");
        }

        @Test
        @DisplayName("processInviteUser — rejects when email already belongs to ACTIVE user")
        void processInviteUser_rejectsActiveUser() {
            User activeUser = User.builder()
                    .id(UUID.randomUUID()).email(newEmail)
                    .status(UserStatus.ACTIVE).fullName("Existing").build();
            when(userInviteTokenRepository.findByEmailIgnoreCase(newEmail)).thenReturn(List.of());
            when(userRepository.findByEmail(newEmail)).thenReturn(Optional.of(activeUser));

            assertThatThrownBy(() -> authService.processInviteUser(newEmail, "SALES"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("đang hoạt động");
        }

        @Test
        @DisplayName("processInviteUser — reuses existing INACTIVE user instead of creating new")
        void processInviteUser_reusesInactiveUser() {
            User inactiveUser = User.builder()
                    .id(UUID.randomUUID()).email(newEmail)
                    .status(UserStatus.INACTIVE).fullName("Old Name").deletedAt(OffsetDateTime.now()).build();
            when(userInviteTokenRepository.findByEmailIgnoreCase(newEmail)).thenReturn(List.of());
            when(userRepository.findByEmail(newEmail)).thenReturn(Optional.of(inactiveUser));
            when(roleRepository.findByName("SALES")).thenReturn(Optional.of(roleSales()));
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
            when(userRoleRepository.findByUserId(inactiveUser.getId())).thenReturn(List.of());
            when(userRoleRepository.save(any(UserRole.class))).thenAnswer(i -> i.getArgument(0));
            when(userInviteTokenRepository.save(any(UserInviteToken.class))).thenAnswer(i -> i.getArgument(0));

            authService.processInviteUser(newEmail, "SALES");

            ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
            verify(userRepository, times(1)).save(userCap.capture());
            User saved = userCap.getValue();
            assertThat(saved.getId()).isEqualTo(inactiveUser.getId());
            assertThat(saved.getFullName()).isEqualTo("Chờ kích hoạt");
            assertThat(saved.getDeletedAt()).isNull();
        }

        @Test
        @DisplayName("processInviteUser — throws when role not found in DB")
        void processInviteUser_roleMissing() {
            when(userInviteTokenRepository.findByEmailIgnoreCase(newEmail)).thenReturn(List.of());
            when(userRepository.findByEmail(newEmail)).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
            when(userRoleRepository.findByUserId(any(UUID.class))).thenReturn(List.of());
            when(roleRepository.findByName("SALES")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.processInviteUser(newEmail, "SALES"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Vai trò không tồn tại");
        }

        @Test
        @DisplayName("processInviteUser — updates existing UserRole instead of creating duplicate")
        void processInviteUser_updatesExistingUserRole() {
            User inactiveUser = User.builder()
                    .id(UUID.randomUUID()).email(newEmail)
                    .status(UserStatus.INACTIVE).fullName("Old").build();
            UserRole existingUr = UserRole.builder().id(UUID.randomUUID()).user(inactiveUser).build();

            when(userInviteTokenRepository.findByEmailIgnoreCase(newEmail)).thenReturn(List.of());
            when(userRepository.findByEmail(newEmail)).thenReturn(Optional.of(inactiveUser));
            when(roleRepository.findByName("SALES")).thenReturn(Optional.of(roleSales()));
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
            when(userRoleRepository.findByUserId(inactiveUser.getId())).thenReturn(List.of(existingUr));
            when(userRoleRepository.save(any(UserRole.class))).thenAnswer(i -> i.getArgument(0));
            when(userInviteTokenRepository.save(any(UserInviteToken.class))).thenAnswer(i -> i.getArgument(0));

            authService.processInviteUser(newEmail, "SALES");

            verify(userRoleRepository).save(existingUr);
            assertThat(existingUr.getRole().getName()).isEqualTo("SALES");
            assertThat(existingUr.getGrantedAt()).isNotNull();
        }

        @Test
        @DisplayName("validateInviteToken — throws when token not found")
        void validateInviteToken_notFound() {
            when(userInviteTokenRepository.findByToken("nope")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.validateInviteToken("nope"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Liên kết không hợp lệ");
        }

        @Test
        @DisplayName("validateInviteToken — throws when token already used")
        void validateInviteToken_alreadyUsed() {
            UserInviteToken used = pendingToken();
            used.setUsedAt(OffsetDateTime.now().minusMinutes(1));
            when(userInviteTokenRepository.findByToken(used.getToken())).thenReturn(Optional.of(used));

            assertThatThrownBy(() -> authService.validateInviteToken(used.getToken()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("đã được sử dụng");
        }

        @Test
        @DisplayName("validateInviteToken — throws when token CANCELLED")
        void validateInviteToken_cancelled() {
            UserInviteToken cancelled = pendingToken();
            cancelled.setStatus("CANCELLED");
            when(userInviteTokenRepository.findByToken(cancelled.getToken())).thenReturn(Optional.of(cancelled));

            assertThatThrownBy(() -> authService.validateInviteToken(cancelled.getToken()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("đã bị hủy");
        }

        @Test
        @DisplayName("validateInviteToken — throws when token expired")
        void validateInviteToken_expired() {
            UserInviteToken expired = pendingToken();
            expired.setExpiresAt(OffsetDateTime.now().minusMinutes(1));
            when(userInviteTokenRepository.findByToken(expired.getToken())).thenReturn(Optional.of(expired));

            assertThatThrownBy(() -> authService.validateInviteToken(expired.getToken()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("đã hết hạn");
        }

        @Test
        @DisplayName("validateInviteToken — returns token when valid")
        void validateInviteToken_happy() {
            UserInviteToken ok = pendingToken();
            when(userInviteTokenRepository.findByToken(ok.getToken())).thenReturn(Optional.of(ok));

            UserInviteToken result = authService.validateInviteToken(ok.getToken());

            assertThat(result).isSameAs(ok);
        }

        @Test
        @DisplayName("acceptInvite — throws when password != confirmPassword")
        void acceptInvite_passwordMismatch() {
            AcceptInviteRequest req = AcceptInviteRequest.builder()
                    .token("t").fullName("FN").password("Password123@").confirmPassword("Different1!").build();

            assertThatThrownBy(() -> authService.acceptInvite(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("không khớp");
        }

        @Test
        @DisplayName("acceptInvite — throws on weak password")
        void acceptInvite_weakPassword() {
            AcceptInviteRequest req = AcceptInviteRequest.builder()
                    .token("t").fullName("FN").password("weak").confirmPassword("weak").build();

            assertThatThrownBy(() -> authService.acceptInvite(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("không đúng định dạng");
        }

        @Test
        @DisplayName("acceptInvite — happy path: new user created, role assigned, token ACCEPTED, audit log written")
        void acceptInvite_happy_newUser() {
            UserInviteToken ok = pendingToken();
            AcceptInviteRequest req = AcceptInviteRequest.builder()
                    .token(ok.getToken())
                    .fullName("  New Staff  ")
                    .password("Password123@")
                    .confirmPassword("Password123@")
                    .build();

            when(userInviteTokenRepository.findByToken(ok.getToken())).thenReturn(Optional.of(ok));
            when(userRepository.findByEmail(ok.getEmail())).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
            when(roleRepository.findByName(ok.getRoleName())).thenReturn(Optional.of(roleSales()));
            when(userRoleRepository.findByUserId(any(UUID.class))).thenReturn(List.of());
            when(userRoleRepository.save(any(UserRole.class))).thenAnswer(i -> i.getArgument(0));
            when(userInviteTokenRepository.save(any(UserInviteToken.class))).thenAnswer(i -> i.getArgument(0));
            when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));
            when(passwordEncoder.encode("Password123@")).thenReturn("hashed-password");

            authService.acceptInvite(req);

            ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCap.capture());
            User savedUser = userCap.getValue();
            assertThat(savedUser.getEmail()).isEqualTo(ok.getEmail());
            assertThat(savedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(savedUser.getFullName()).isEqualTo("New Staff");
            assertThat(savedUser.getPasswordHash()).isEqualTo("hashed-password");

            ArgumentCaptor<UserInviteToken> tokenCap = ArgumentCaptor.forClass(UserInviteToken.class);
            verify(userInviteTokenRepository).save(tokenCap.capture());
            assertThat(tokenCap.getValue().getStatus()).isEqualTo("ACCEPTED");
            assertThat(tokenCap.getValue().getUsedAt()).isNotNull();

            ArgumentCaptor<AuditLog> auditCap = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(auditCap.capture());
            AuditLog audit = auditCap.getValue();
            assertThat(audit.getAction()).isEqualTo("CREATE");
            assertThat(audit.getEntityType()).isEqualTo("USER");
            assertThat(audit.getChanges()).containsEntry("action", "ACCEPT_INVITE");
        }

        @Test
        @DisplayName("acceptInvite — reuses existing INACTIVE user")
        void acceptInvite_reactivatesInactiveUser() {
            UserInviteToken ok = pendingToken();
            User inactiveUser = User.builder()
                    .id(UUID.randomUUID()).email(ok.getEmail())
                    .status(UserStatus.INACTIVE).fullName("Old").build();
            AcceptInviteRequest req = AcceptInviteRequest.builder()
                    .token(ok.getToken())
                    .fullName("Activated")
                    .password("Password123@")
                    .confirmPassword("Password123@")
                    .build();

            when(userInviteTokenRepository.findByToken(ok.getToken())).thenReturn(Optional.of(ok));
            when(userRepository.findByEmail(ok.getEmail())).thenReturn(Optional.of(inactiveUser));
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
            when(roleRepository.findByName(ok.getRoleName())).thenReturn(Optional.of(roleSales()));
            when(userRoleRepository.findByUserId(inactiveUser.getId())).thenReturn(List.of());
            when(userRoleRepository.save(any(UserRole.class))).thenAnswer(i -> i.getArgument(0));
            when(userInviteTokenRepository.save(any(UserInviteToken.class))).thenAnswer(i -> i.getArgument(0));
            when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));
            when(passwordEncoder.encode("Password123@")).thenReturn("hashed-password");

            authService.acceptInvite(req);

            ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCap.capture());
            assertThat(userCap.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(userCap.getValue().getFullName()).isEqualTo("Activated");
        }

        @Test
        @DisplayName("acceptInvite — rejects when target user is already ACTIVE")
        void acceptInvite_rejectsActiveUser() {
            UserInviteToken ok = pendingToken();
            User activeUser = User.builder()
                    .id(UUID.randomUUID()).email(ok.getEmail())
                    .status(UserStatus.ACTIVE).fullName("Existing").build();
            AcceptInviteRequest req = AcceptInviteRequest.builder()
                    .token(ok.getToken())
                    .fullName("X").password("Password123@").confirmPassword("Password123@").build();

            when(userInviteTokenRepository.findByToken(ok.getToken())).thenReturn(Optional.of(ok));
            when(userRepository.findByEmail(ok.getEmail())).thenReturn(Optional.of(activeUser));

            assertThatThrownBy(() -> authService.acceptInvite(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("đang hoạt động");
        }
    }
}
