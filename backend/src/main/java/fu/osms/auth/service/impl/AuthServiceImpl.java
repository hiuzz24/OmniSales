package fu.osms.auth.service.impl;

import fu.osms.audit.entity.AuditLog;
import fu.osms.audit.repository.AuditLogRepository;
import fu.osms.auth.dto.request.LoginRequest;
import fu.osms.auth.dto.request.ChangePasswordRequest;
import fu.osms.auth.dto.request.ResetPasswordRequest;
import fu.osms.auth.dto.response.AuthResponse;
import fu.osms.auth.dto.response.ResetPasswordResponse;
import fu.osms.auth.dto.response.TokenPairDTO;
import fu.osms.auth.dto.response.UserResponse;
import fu.osms.auth.entity.*;
import fu.osms.auth.enums.UserStatus;
import fu.osms.auth.mapper.UserMapper;
import fu.osms.auth.repository.*;
import fu.osms.auth.security.JwtService;
import fu.osms.auth.service.AuthService;
import fu.osms.auth.service.EmailService;
import fu.osms.config.CustomUserDetailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final CustomUserDetailService userDetailsService;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogRepository auditLogRepository;
    private final RoleRepository roleRepository;
    @Value("${app.security.max-failed-attempts}")
    private int maxFailedAttempts;

    @Value("${app.security.lock-time-duration}")
    private int lockTimeDuration;

    @Override
    @Transactional(noRollbackFor = {AuthenticationException.class, AppException.class})
    public TokenPairDTO login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_CREDENTIALS));

        if (user.getLockedUntil() != null) {
            if(user.getLockedUntil().isAfter(OffsetDateTime.now())){
                long minutesLeft = java.time.temporal.ChronoUnit.MINUTES.between(OffsetDateTime.now(), user.getLockedUntil()) + 1;
                throw new AppException(ErrorCode.ACCOUNT_LOCKED, "Tài khoản đang bị khóa. Vui lòng thử lại sau " + minutesLeft + " phút.");
            }else{
                user.setFailedLoginAttempts(0);
                user.setLockedUntil(null);
                user.setStatus(UserStatus.ACTIVE);
                userRepository.save(user);
            }
        }

        try{
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
            if(user.getFailedLoginAttempts() > 0){
                user.setFailedLoginAttempts(0);
            }

        }catch(AuthenticationException e){
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);

            if(attempts >= maxFailedAttempts){
                user.setLockedUntil(OffsetDateTime.now().plusMinutes(lockTimeDuration));
                user.setStatus(UserStatus.LOCKED);
                userRepository.save(user);
                throw new AppException(ErrorCode.ACCOUNT_LOCKED, "Bạn đã nhập sai " + maxFailedAttempts + " lần. Tài khoản bị khóa " + lockTimeDuration + " phút.");
            }else{
                userRepository.save(user);
                throw new AppException(ErrorCode.INVALID_CREDENTIALS, "Sai mật khẩu. Bạn còn " + (maxFailedAttempts - attempts) + " lần thử.");
            }
        }

        if (Boolean.TRUE.equals(user.getPasswordExpired())) {
            log.info("User {} đăng nhập bằng mật khẩu tạm thời. Yêu cầu đổi mật khẩu sau khi đăng nhập.", user.getEmail());
        }
        var roles = userRoleRepository.findByUserId(user.getId());
        if (roles.isEmpty()) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
        UserRole role = roles.get(0);

        UserResponse response = userMapper.toResponse(user);
        response.setRole(role.getRole().getName());

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String accessToken = jwtService.generateAccessToken(userDetails);
        String refreshTokenValue = jwtService.generateRefreshToken(userDetails);

        String tokenHash = hashToken(refreshTokenValue);
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(OffsetDateTime.now().plusDays(7))
                .build();
        refreshTokenRepository.save(refreshToken);

        return TokenPairDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenValue)
                .user(response)
                .build();
    }

    @Override
    @Transactional
    public TokenPairDTO refreshToken(String refreshToken) {
        String tokenHash = hashToken(refreshToken);
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new AppException(ErrorCode.TOKEN_INVALID));

        if (storedToken.getRevokedAt() != null) {
            throw new AppException(ErrorCode.TOKEN_REVOKED);
        }
        if (storedToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new AppException(ErrorCode.TOKEN_EXPIRED);
        }

        User user = storedToken.getUser();
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String newAccessToken = jwtService.generateAccessToken(userDetails);
        String newRefreshToken = jwtService.generateRefreshToken(userDetails);

        storedToken.setRevokedAt(OffsetDateTime.now());
        refreshTokenRepository.save(storedToken);

        String newHash = hashToken(newRefreshToken);
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(newHash)
                .expiresAt(OffsetDateTime.now().plusDays(7))
                .build());

        return TokenPairDTO.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .user(userMapper.toResponse(user))
                .build();
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        String tokenHash = hashToken(refreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            token.setRevokedAt(OffsetDateTime.now());
            refreshTokenRepository.save(token);
        });
    }

    @Override
    public void processForgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new IllegalArgumentException("Email không có trong hệ thống"));

        String tokenStr = UUID.randomUUID().toString();

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(user);
        resetToken.setToken(tokenStr);
        resetToken.setExpiresAt(OffsetDateTime.now().plusMinutes(15));

        tokenRepository.save(resetToken);

        emailService.sendForgetPasswordEmail(user.getEmail(), tokenStr);

    }

    @Override
    public void validateResetToken(String tokenStr) {
        PasswordResetToken token = tokenRepository.findByToken(tokenStr)
                .orElseThrow(() -> new IllegalArgumentException("Liên kết không hợp lệ hoặc đã bị sử dụng"));

        if (token.getUsedAt() != null) {
            throw new IllegalArgumentException("Liên kết không hợp lệ hoặc đã bị sử dụng");
        }

        if (token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new IllegalArgumentException("Liên kết đã hết hạn, vui lòng yêu cầu lấy lại mật khẩu");
        }
    }

    @Override
    public void updatePassword(ChangePasswordRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Mật khẩu xác nhận không trùng khớp");
        }

        // Kiểm tra tính hợp lệ của token trước khi update
        PasswordResetToken token = tokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new IllegalArgumentException("Liên kết không hợp lệ hoặc đã bị sử dụng"));

        if (token.getUsedAt() != null) {
            throw new IllegalArgumentException("Liên kết không hợp lệ hoặc đã bị sử dụng");
        }

        if (token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new IllegalArgumentException("Liên kết đã hết hạn, vui lòng yêu cầu lấy lại mật khẩu");
        }

        // Tiến hành cập nhật mật khẩu mới của User
        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        user.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);

        // Đánh dấu token đã sử dụng
        token.setUsedAt(OffsetDateTime.now());
        tokenRepository.save(token);

    }


    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new AppException(ErrorCode.TOKEN_INVALID,e.getMessage());
        }
    }

    public boolean isValidPasswordFormat(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }
        String passwordRegex = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#$%^&+=!\\-_]).{8,}$";
        Pattern pattern = Pattern.compile(passwordRegex);
        return pattern.matcher(password).matches();
    }

    private String generateTemporaryPassword() {
        String upperCaseChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lowerCaseChars = "abcdefghijklmnopqrstuvwxyz";
        String numberChars = "0123456789";
        String specialChars = "!@#$%^&*()-_=+";

        SecureRandom random = new SecureRandom();
        List<Character> passwordChars = new ArrayList<>();

        passwordChars.add(upperCaseChars.charAt(random.nextInt(upperCaseChars.length())));
        passwordChars.add(lowerCaseChars.charAt(random.nextInt(lowerCaseChars.length())));
        passwordChars.add(numberChars.charAt(random.nextInt(numberChars.length())));
        passwordChars.add(specialChars.charAt(random.nextInt(specialChars.length())));

        String allChars = upperCaseChars + lowerCaseChars + numberChars + specialChars;
        for (int i = 0; i < 8; i++) {
            passwordChars.add(allChars.charAt(random.nextInt(allChars.length())));
        }

        Collections.shuffle(passwordChars, random);

        StringBuilder password = new StringBuilder();
        for (char c : passwordChars) {
            password.append(c);
        }

        return password.toString();
    }
    @Override
    @Transactional
    public ResetPasswordResponse resetUserPassword(ResetPasswordRequest request, UUID userRequestId) {
        UUID targetUserId = request.getUserId();
        Optional<Role> userRole = roleRepository.findRoleByUserId(targetUserId);

        if (userRole.isPresent()) {
            String roleName = userRole.get().getName();

            if ("ADMIN".equals(roleName) || "OWNER".equals(roleName)) {
                return new ResetPasswordResponse(
                        false,
                        "You cannot reset your own password. Please use 'Forgot Password' instead.",
                        null,
                        null
                );
            }
        }

        User targetUser = userRepository.findActiveById(targetUserId).orElse(null);
        if (targetUser == null) {
            return new ResetPasswordResponse(
                    false,
                    "User not found. Please refresh the page and try again.",
                    null,
                    null
            );
        }
      if (!"ACTIVE".equals(targetUser.getStatus().toString())) {
            return new ResetPasswordResponse(
                    false,
                    "Cannot reset password for inactive or locked accounts. Please enable the account first.",
                    targetUser.getEmail(),
                    null
            );
        }

        String tempPassword = generateTemporaryPassword();

        if (!isValidPasswordFormat(tempPassword)) {
            return new ResetPasswordResponse(
                    false,
                    "Unable to reset password. Please try again.",
                    targetUser.getEmail(),
                    null
            );
        }


        targetUser.setPasswordHash(passwordEncoder.encode(tempPassword));
        targetUser.setPasswordExpired(true);
        userRepository.save(targetUser);


        refreshTokenRepository.revokeAllByUserId(targetUserId);

        User admin = userRepository.findUserById(userRequestId);


        AuditLog log = new AuditLog();
        log.setActor(admin);
        log.setActorEmail(admin.getEmail());
        log.setAction("UPDATE");
        log.setEntityType("USER");
        log.setEntityId(targetUserId);
        log.setEntityName(targetUser.getFullName());
        java.util.Map<String, Object> changesMap = new java.util.HashMap<>();
        changesMap.put("action", "RESET_PASSWORD");
        changesMap.put("password_expired", true);
        log.setChanges(changesMap);
        log.setPerformedAt(java.time.OffsetDateTime.now());

        auditLogRepository.save(log);


        try {
            emailService.sentResetPasswordEmail(targetUser.getEmail(), targetUser.getFullName(), tempPassword);
        } catch (Exception e) {return new ResetPasswordResponse(
                    true,
                    "Password was reset but email failed to send. Please provide the temporary password manually to the user.",
                    targetUser.getEmail(),
                    tempPassword
            );
        }

        return new ResetPasswordResponse(
                true,
                "Password reset successfully. A new temporary password has been sent to " + targetUser.getEmail(),
                targetUser.getEmail(),
                null
        );
    }

    @Override
    @Transactional
    public void changePasswordAfterLogin(UUID userId, String oldPassword, String newPassword, String confirmPassword) {
        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("Mật khẩu xác nhận không trùng khớp");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Mật khẩu cũ không chính xác");
        }

        if (!isValidPasswordFormat(newPassword)) {
            throw new IllegalArgumentException("Mật khẩu mới không đúng định dạng quy định");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordExpired(false);
        user.setUpdatedAt(OffsetDateTime.now());

        userRepository.save(user);

        refreshTokenRepository.revokeAllByUserId(userId);
    }
}
