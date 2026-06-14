package fu.osms.auth.service.impl;

import fu.osms.auth.dto.request.LoginRequest;
import fu.osms.auth.dto.response.AuthResponse;
import fu.osms.auth.dto.response.TokenPairDTO;
import fu.osms.auth.dto.response.UserResponse;
import fu.osms.auth.entity.RefreshToken;
import fu.osms.auth.entity.User;
import fu.osms.auth.entity.UserRole;
import fu.osms.auth.enums.UserStatus;
import fu.osms.auth.mapper.UserMapper;
import fu.osms.auth.repository.RefreshTokenRepository;
import fu.osms.auth.repository.UserRepository;
import fu.osms.auth.repository.UserRoleRepository;
import fu.osms.auth.security.JwtService;
import fu.osms.auth.service.AuthService;
import fu.osms.config.CustomUserDetailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Base64;

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


    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new AppException(ErrorCode.TOKEN_INVALID,e.getMessage());
        }
    }
}
