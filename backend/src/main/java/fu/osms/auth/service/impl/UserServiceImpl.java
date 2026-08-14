package fu.osms.auth.service.impl;

import fu.osms.auth.dto.request.UpdateProfileRequest;
import fu.osms.auth.dto.request.UserRequest;
import fu.osms.auth.dto.response.ResetPasswordResponse;
import fu.osms.auth.dto.response.UserProfileResponse;
import fu.osms.auth.dto.response.UserResponse;
import fu.osms.auth.entity.User;
import fu.osms.auth.entity.UserRole;
import fu.osms.auth.enums.UserStatus;
import fu.osms.auth.mapper.UserMapper;
import fu.osms.auth.entity.Role;
import fu.osms.auth.repository.RoleRepository;
import fu.osms.auth.repository.UserRepository;
import fu.osms.auth.repository.UserRoleRepository;
import fu.osms.auth.repository.UserInviteTokenRepository;
import fu.osms.auth.entity.UserInviteToken;
import fu.osms.auth.service.UserService;
import fu.osms.common.dto.PageResponse;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.system.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.BadRequestException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final UserInviteTokenRepository userInviteTokenRepository;
    private final SystemSettingService systemSettingService;

    // ════════════════════════════════════════════════════════════════════════
    // CRUD
    // ════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public UserResponse create(UserRequest request) {
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("Mật khẩu không được để trống khi tạo mới tài khoản");
        }
        if (!isValidPasswordFormat(request.getPassword())) {
            throw new IllegalArgumentException("Mật khẩu không đáp ứng chính sách bảo mật hiện tại");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email is already in use: " + request.getEmail());
        }
        User user = userMapper.toEntity(request);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setPasswordChangedAt(java.time.OffsetDateTime.now());
        user.setPasswordExpired(false);
        user.setStatus(UserStatus.ACTIVE);
        
        User savedUser = userRepository.save(user);

        // Assign role if provided
        if (request.getRole() != null && !request.getRole().trim().isEmpty()) {
            String roleName = request.getRole().trim().toUpperCase();
            if ("SALES STAFF".equals(roleName)) {
                roleName = "SALES";
            } else if ("OPERATIONS STAFF".equals(roleName)) {
                roleName = "OPERATIONS";
            }

            final String finalRoleName = roleName;
            Role role = roleRepository.findByName(finalRoleName)
                    .orElseThrow(() -> new IllegalArgumentException("Vai trò không tồn tại: " + finalRoleName));

            UserRole userRole = UserRole.builder()
                    .user(savedUser)
                    .role(role)
                    .grantedAt(java.time.OffsetDateTime.now())
                    .build();
            userRoleRepository.save(userRole);
        }

        return convertToUserResponse(savedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getById(UUID id) {
        return userRepository.findById(id)
                .map(this::convertToUserResponse)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(this::convertToUserResponse)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> getAll(int page, int size) {
        Page<User> pageResult = userRepository.findAll(PageRequest.of(page, size));
        List<UserResponse> content = pageResult.getContent().stream()
                .map(this::convertToUserResponse)
                .toList();
        return PageResponse.<UserResponse>builder()
                .content(content)
                .page(page).size(size)
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .first(pageResult.isFirst()).last(pageResult.isLast())
                .build();
    }

    @Override
    @Transactional
    public UserResponse update(UUID id, UserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        
        userMapper.updateEntityFromRequest(request, user);

        // Update status if provided
        if (request.getStatus() != null && !request.getStatus().trim().isEmpty()) {
            try {
                UserStatus newStatus = UserStatus.valueOf(request.getStatus().trim().toUpperCase());
                user.setStatus(newStatus);
                if (newStatus == UserStatus.ACTIVE) {
                    user.setDeletedAt(null);
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Trạng thái không hợp lệ: " + request.getStatus());
            }
        }

        User savedUser = userRepository.save(user);

        // Update role if provided
        if (request.getRole() != null && !request.getRole().trim().isEmpty()) {
            String roleName = request.getRole().trim().toUpperCase();
            if ("SALES STAFF".equals(roleName)) {
                roleName = "SALES";
            } else if ("OPERATIONS STAFF".equals(roleName)) {
                roleName = "OPERATIONS";
            }

            final String finalRoleName = roleName;
            Role role = roleRepository.findByName(finalRoleName)
                    .orElseThrow(() -> new IllegalArgumentException("Vai trò không tồn tại: " + finalRoleName));

            List<UserRole> existingRoles = userRoleRepository.findByUserId(savedUser.getId());
            UserRole userRole;
            if (!existingRoles.isEmpty()) {
                userRole = existingRoles.get(0);
                userRole.setRole(role);
                userRole.setGrantedAt(java.time.OffsetDateTime.now());
            } else {
                userRole = UserRole.builder()
                        .user(savedUser)
                        .role(role)
                        .grantedAt(java.time.OffsetDateTime.now())
                        .build();
            }
            userRoleRepository.save(userRole);
        }

        return convertToUserResponse(savedUser);
    }

    @Override
    @Transactional
    public void changePassword(UUID id, String oldPassword, String newPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Mật khẩu cũ không đúng");
        }
        if (!isValidPasswordFormat(newPassword)) {
            throw new IllegalArgumentException("Mật khẩu mới không đáp ứng chính sách bảo mật hiện tại");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(java.time.OffsetDateTime.now());
        user.setPasswordExpired(false);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        user.setStatus(UserStatus.INACTIVE);
        user.setDeletedAt(java.time.OffsetDateTime.now());
        userRepository.save(user);
    }

    // ════════════════════════════════════════════════════════════════════════
    // Profile (current user)
    // ════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return mapToProfileResponse(user);
    }

    @Override
    @Transactional
    public UserProfileResponse updateMyProfile(String email, UpdateProfileRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName().trim());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone().trim().isEmpty() ? null : request.getPhone().trim());
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl().trim().isEmpty() ? null : request.getAvatarUrl().trim());
        }

        User saved = userRepository.save(user);
        log.info("Profile updated for user: {}", email);
        return mapToProfileResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getProfileById(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return mapToProfileResponse(user);
    }

    @Override
    @Transactional
    public void changePassword(String email, String oldPassword, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Mật khẩu cũ không đúng");
        }
        if (!isValidPasswordFormat(newPassword)) {
            throw new IllegalArgumentException("Mật khẩu mới không đáp ứng chính sách bảo mật hiện tại");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(java.time.OffsetDateTime.now());
        user.setPasswordExpired(false);
        userRepository.save(user);
        log.info("Password changed for user: {}", email);
    }
    public boolean isValidPasswordFormat(String password) {
        int minLength = Math.max(systemSettingService.getInteger("password_min_length", 8), 6);
        if (password == null || password.length() < minLength) {
            return false;
        }
        if (systemSettingService.getBoolean("password_require_lowercase", true)
                && password.chars().noneMatch(Character::isLowerCase)) return false;
        if (systemSettingService.getBoolean("password_require_uppercase", true)
                && password.chars().noneMatch(Character::isUpperCase)) return false;
        if (systemSettingService.getBoolean("password_require_number", true)
                && password.chars().noneMatch(Character::isDigit)) return false;
        return !systemSettingService.getBoolean("password_require_special_character", true)
                || password.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch));
    }
    @Override
    public UUID findUserIdByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new IllegalArgumentException("User not found"))
                .getId();
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private UserProfileResponse mapToProfileResponse(User user) {
        String roleName = null;
        List<UserRole> roles = userRoleRepository.findByUserId(user.getId());
        if (!roles.isEmpty()) {
            roleName = roles.get(0).getRole().getName();
        }

        return UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .role(roleName)
                .createdAt(user.getCreatedAt())
                .build();
    }

    @Override
    @Transactional
    public void cancelInvite(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (user.getStatus() != UserStatus.INACTIVE || user.getDeletedAt() != null) {
            throw new IllegalArgumentException("Chỉ có thể hủy lời mời đối với tài khoản chưa kích hoạt");
        }

        // Update invite tokens associated with this email to CANCELLED instead of deleting them
        List<UserInviteToken> tokens = userInviteTokenRepository.findAll().stream()
                .filter(t -> t.getEmail().equalsIgnoreCase(user.getEmail()))
                .toList();
        for (UserInviteToken token : tokens) {
            if (token.getUsedAt() == null && !"CANCELLED".equals(token.getStatus())) {
                token.setStatus("CANCELLED");
                userInviteTokenRepository.save(token);
            }
        }

        // Delete user roles
        List<UserRole> roles = userRoleRepository.findByUserId(user.getId());
        userRoleRepository.deleteAll(roles);

        // Delete the user record completely
        userRepository.delete(user);

        log.info("Invitation cancelled and user deleted for email: {}", user.getEmail());
    }

    @Override
    public List<fu.osms.auth.dto.response.UserInviteResponse> getInvitations() {
        return userInviteTokenRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(token -> {
                    String displayStatus = token.getStatus();
                    if (!"CANCELLED".equalsIgnoreCase(displayStatus)) {
                        if (token.getUsedAt() != null) {
                            displayStatus = "ACCEPTED";
                        } else if (token.getExpiresAt().isBefore(java.time.OffsetDateTime.now())) {
                            displayStatus = "EXPIRED";
                        } else {
                            displayStatus = "PENDING";
                        }
                    }
                    return fu.osms.auth.dto.response.UserInviteResponse.builder()
                            .id(token.getId())
                            .email(token.getEmail())
                            .roleName(token.getRoleName())
                            .token(token.getToken())
                            .expiresAt(token.getExpiresAt())
                            .usedAt(token.getUsedAt())
                            .createdAt(token.getCreatedAt())
                            .status(displayStatus)
                            .build();
                })
                .toList();
    }

    @Override
    @Transactional
    public void cancelInviteByTokenId(UUID tokenId) {
        UserInviteToken token = userInviteTokenRepository.findById(tokenId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông tin lời mời"));

        if (token.getUsedAt() != null) {
            throw new IllegalArgumentException("Không thể hủy lời mời đã được chấp nhận");
        }

        token.setStatus("CANCELLED");
        userInviteTokenRepository.save(token);

        userRepository.findByEmail(token.getEmail()).ifPresent(user -> {
            if (user.getStatus() == UserStatus.INACTIVE && "Chờ kích hoạt".equals(user.getFullName())) {
                List<UserRole> roles = userRoleRepository.findByUserId(user.getId());
                userRoleRepository.deleteAll(roles);
                userRepository.delete(user);
                log.info("Deleted inactive user record for email: {}", user.getEmail());
            }
        });

        log.info("Invitation cancelled for token ID: {}", tokenId);
    }

    private UserResponse convertToUserResponse(User user) {
        UserResponse res = userMapper.toResponse(user);
        List<UserRole> roles = userRoleRepository.findByUserId(user.getId());
        if (!roles.isEmpty()) {
            res.setRole(roles.get(0).getRole().getName());
        }
        
        if (user.getStatus() == UserStatus.INACTIVE && "Chờ kích hoạt".equals(user.getFullName())) {
            List<UserInviteToken> tokens = userInviteTokenRepository.findByEmailIgnoreCase(user.getEmail());
            if (!tokens.isEmpty()) {
                UserInviteToken latestToken = tokens.stream()
                        .max((t1, t2) -> t1.getCreatedAt().compareTo(t2.getCreatedAt()))
                        .orElse(null);
                if (latestToken != null) {
                    String tokenStatus = latestToken.getStatus();
                    if (!"CANCELLED".equalsIgnoreCase(tokenStatus)) {
                        if (latestToken.getUsedAt() != null) {
                            tokenStatus = "ACCEPTED";
                        } else if (latestToken.getExpiresAt().isBefore(java.time.OffsetDateTime.now())) {
                            tokenStatus = "EXPIRED";
                        } else {
                            tokenStatus = "PENDING";
                        }
                    }
                    res.setInviteStatus(tokenStatus);
                }
            }
        }
        return res;
    }
}
