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
import fu.osms.auth.dto.request.RegisterRequest;
import fu.osms.shop.entity.Shop;
import fu.osms.shop.repository.ShopRepository;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.common.service.EmailService;
import fu.osms.auth.repository.RoleRepository;
import fu.osms.auth.enums.UserStatus;
import fu.osms.auth.mapper.RegisterMapper;
import fu.osms.auth.entity.Role;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

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
    private final RegisterMapper registerMapper;
    private final RoleRepository roleRepository;
    private final ShopRepository shopRepository;
    private final EmailService emailService;

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

    @Override
    @Transactional
    public void register(RegisterRequest request) {

        validateRegisterRequest(request);

        Optional<User> existingUserOpt =
                userRepository.findByEmail(request.getEmail());

        if(existingUserOpt.isPresent()) {

            User existingUser = existingUserOpt.get();

            if(existingUser.getStatus() == UserStatus.ACTIVE) {
                throw new AppException(
                        ErrorCode.EMAIL_ALREADY_EXISTS,
                        "Email đã được sử dụng"
                );
            }

            updateInactiveAccount(existingUser, request);

            return;
        }

        createNewAccount(request);
    }

    private void validateRegisterRequest(
            RegisterRequest request
    ) {

        if (!request.getPassword()
                .equals(request.getConfirmPassword())) {

            throw new AppException(
                    ErrorCode.VALIDATION_FAILED,
                    "Mật khẩu xác nhận không khớp"
            );
        }
        if (shopRepository.existsBySlug(generateShopSlug(request.getShopName()))) {
            throw new AppException(
                    ErrorCode.SHOP_SLUG_CONFLICT,
                    "Tên cửa hàng đã tồn tại"
            );
        }

        if (userRepository.existsByPhone(request.getPhone())) {
            throw new AppException(
                    ErrorCode.PHONE_ALREADY_EXISTS,
                    "Số điện thoại đã được sử dụng"
            );
        }
    }

    private void createNewAccount(
            RegisterRequest request
    ) {

        String token = UUID.randomUUID().toString();

        User user = registerMapper.toUser(request);

        user.setPasswordHash(
                passwordEncoder.encode(
                        request.getPassword()
                )
        );

        user.setStatus(UserStatus.INACTIVE);

        user.setVerificationToken(token);

        user.setVerificationTokenExpiresAt(
                OffsetDateTime.now().plusMinutes(15)
        );

        userRepository.save(user);

        Shop shop = registerMapper.toShop(request);

        shop.setIsActive(false);

        shop.setAllowNegativeStock(false);

        shop.setCostingMethod("WAC");

        shop.setSlug(generateShopSlug(
                request.getShopName()
        ));

        shopRepository.save(shop);

        Role ownerRole =
                roleRepository.findByName("OWNER")
                        .orElseThrow();

        UserRole userRole =
                UserRole.builder()
                        .user(user)
                        .shop(shop)
                        .role(ownerRole)
                        .grantedBy(user)
                        .build();

        userRoleRepository.save(userRole);

        emailService.sendVerificationEmail(
                user.getEmail(),
                user.getFullName(),
                token
        );
    }

    private void updateInactiveAccount(
            User user,
            RegisterRequest request
    ) {

        user.setFullName(
                request.getFullName()
        );

        user.setPhone(
                request.getPhone()
        );

        user.setPasswordHash(
                passwordEncoder.encode(
                        request.getPassword()
                )
        );

        String newToken =
                UUID.randomUUID().toString();

        user.setVerificationToken(
                newToken
        );

        user.setVerificationTokenExpiresAt(
                OffsetDateTime.now().plusMinutes(15)
        );

        userRepository.save(user);

        UserRole userRole =
                userRoleRepository
                        .findByUserId(user.getId())
                        .orElseThrow();

        Shop shop = userRole.getShop();

        shop.setName(
                request.getShopName()
        );

        shopRepository.save(shop);

        emailService.sendVerificationEmail(
                user.getEmail(),
                user.getFullName(),
                newToken
        );
    }

    private String generateShopSlug(String shopName) {

        return shopName
                .toLowerCase()
                .trim()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }

    @Override
    @Transactional
    public void verifyEmail(
            String token
    ) {

        User user = (User) userRepository
                .findByVerificationToken(token)
                .orElseThrow(() ->
                        new AppException(
                                ErrorCode.INVALID_DATA,
                                "Token không hợp lệ"
                        ));

        if(user.getVerificationTokenExpiresAt()
                .isBefore(OffsetDateTime.now())) {

            throw new AppException(
                    ErrorCode.INVALID_DATA,
                    "Token đã hết hạn"
            );
        }

        user.setStatus(UserStatus.ACTIVE);

        user.setEmailVerifiedAt(
                OffsetDateTime.now()
        );

        userRepository.save(user);

        UserRole role = userRoleRepository
                .findByUserId(user.getId())
                .orElseThrow();

        Shop shop = role.getShop();

        shop.setIsActive(true);

        shopRepository.save(shop);
    }

    @Override
    @Transactional
    public void resendVerificationEmail(
            String email
    ) {

        User user =
                userRepository.findByEmail(email)
                        .orElseThrow(() ->
                                new AppException(
                                        ErrorCode.RESOURCE_NOT_FOUND,
                                        "Không tìm thấy user"
                                ));

        if(user.getStatus() == UserStatus.ACTIVE) {

            throw new AppException(
                    ErrorCode.CONFLICT,
                    "Email đã được xác thực"
            );
        }

        String newToken =
                UUID.randomUUID().toString();

        user.setVerificationToken(
                newToken
        );

        user.setVerificationTokenExpiresAt(
                OffsetDateTime.now().plusMinutes(15)
        );

        userRepository.save(user);

        emailService.sendVerificationEmail(
                user.getEmail(),
                user.getFullName(),
                newToken
        );
    }
}
