package fu.osms.auth.service.impl;

import fu.osms.auth.dto.request.LoginRequest;
import fu.osms.auth.dto.response.AuthResponse;
import fu.osms.auth.dto.response.TokenPairDTO;
import fu.osms.auth.dto.response.UserResponse;
import fu.osms.auth.entity.RefreshToken;
import fu.osms.auth.entity.User;
import fu.osms.auth.entity.UserRole;
import fu.osms.auth.mapper.UserMapper;
import fu.osms.auth.repository.RefreshTokenRepository;
import fu.osms.auth.repository.UserRepository;
import fu.osms.auth.repository.UserRoleRepository;
import fu.osms.auth.security.JwtService;
import fu.osms.auth.service.AuthService;
import fu.osms.config.CustomUserDetailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public TokenPairDTO login(LoginRequest request) {
        try{
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        }catch(BadCredentialsException e){
            log.info("username or pw not correct");
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserRole role = userRoleRepository.findByUserId(user.getId())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

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
    public AuthResponse refreshToken(String refreshToken) {
        String tokenHash = hashToken(refreshToken);
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new RuntimeException("Refresh token is invalid"));

        if (storedToken.getRevokedAt() != null) {
            throw new RuntimeException("Refresh token has been revoked");
        }
        if (storedToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new RuntimeException("Refresh token has expired");
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

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpirationMs() / 1000)
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
            throw new RuntimeException("Error hashing token", e);
        }
    }
}
