package fu.osms.auth.service.impl;

import fu.osms.auth.dto.request.UpdateProfileRequest;
import fu.osms.auth.dto.request.UserRequest;
import fu.osms.auth.dto.response.UserProfileResponse;
import fu.osms.auth.dto.response.UserResponse;
import fu.osms.auth.entity.Role;
import fu.osms.auth.entity.User;
import fu.osms.auth.entity.UserInviteToken;
import fu.osms.auth.entity.UserRole;
import fu.osms.auth.enums.UserStatus;
import fu.osms.auth.mapper.UserMapper;
import fu.osms.auth.repository.RoleRepository;
import fu.osms.auth.repository.UserInviteTokenRepository;
import fu.osms.auth.repository.UserRepository;
import fu.osms.auth.repository.UserRoleRepository;
import fu.osms.common.dto.PageResponse;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl Tests")
class UserServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UserInviteTokenRepository userInviteTokenRepository;

    @InjectMocks private UserServiceImpl userService;

    private User testUser;
    private Role testRole;
    private UserRole testUserRole;
    private UserRequest testUserRequest;
    private UserResponse testUserResponse;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .passwordHash("hashedPassword")
                .fullName("Test User")
                .phone("0909123456")
                .status(UserStatus.ACTIVE)
                .createdAt(OffsetDateTime.now())
                .build();

        testRole = Role.builder()
                .id(UUID.randomUUID())
                .name("SALES")
                .description("Sales role")
                .build();

        testUserRole = UserRole.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .role(testRole)
                .grantedAt(OffsetDateTime.now())
                .build();

        testUserRequest = UserRequest.builder()
                .email("new@example.com")
                .password("NewPass123@")
                .fullName("New User")
                .role("SALES")
                .build();

        testUserResponse = UserResponse.builder()
                .id(testUser.getId())
                .email(testUser.getEmail())
                .fullName(testUser.getFullName())
                .role("SALES")
                .status(UserStatus.ACTIVE)
                .build();
    }

    // ════════════════════════════════════════════════════════════════════════════
    // User Creation
    // ════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("User Creation")
    class UserCreation {

        @Test
        @DisplayName("create - Success")
        void createUser_Success() {
            when(userRepository.existsByEmail(testUserRequest.getEmail())).thenReturn(false);
            when(userMapper.toEntity(testUserRequest)).thenReturn(testUser);
            when(passwordEncoder.encode(testUserRequest.getPassword())).thenReturn("hashedPassword");
            when(userRepository.save(any(User.class))).thenReturn(testUser);
            when(roleRepository.findByName("SALES")).thenReturn(Optional.of(testRole));
            when(userRoleRepository.save(any(UserRole.class))).thenReturn(testUserRole);
            when(userMapper.toResponse(testUser)).thenReturn(testUserResponse);
            when(userRoleRepository.findByUserId(testUser.getId())).thenReturn(List.of(testUserRole));

            UserResponse result = userService.create(testUserRequest);

            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo(testUser.getEmail());
            verify(userRepository).save(any(User.class));
            verify(userRoleRepository).save(any(UserRole.class));
        }

        @Test
        @DisplayName("create - Duplicate email throws IllegalArgumentException")
        void createUser_DuplicateEmail_ThrowsException() {
            when(userRepository.existsByEmail(testUserRequest.getEmail())).thenReturn(true);

            assertThatThrownBy(() -> userService.create(testUserRequest))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Email is already in use");
        }

        @Test
        @DisplayName("create - Blank password throws IllegalArgumentException")
        void createUser_BlankPassword_ThrowsException() {
            UserRequest noPasswordRequest = UserRequest.builder()
                    .email("test@example.com")
                    .password("")
                    .fullName("Test")
                    .build();

            assertThatThrownBy(() -> userService.create(noPasswordRequest))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Mật khẩu không được để trống");
        }

        @Test
        @DisplayName("create - Invalid role throws IllegalArgumentException")
        void createUser_InvalidRole_ThrowsException() {
            UserRequest invalidRoleRequest = UserRequest.builder()
                    .email("test@example.com")
                    .password("Pass123@")
                    .fullName("Test")
                    .role("INVALID_ROLE")
                    .build();

            when(userRepository.existsByEmail(invalidRoleRequest.getEmail())).thenReturn(false);
            when(userMapper.toEntity(invalidRoleRequest)).thenReturn(testUser);
            when(passwordEncoder.encode(any())).thenReturn("hashed");
            when(userRepository.save(any(User.class))).thenReturn(testUser);
            when(roleRepository.findByName("INVALID_ROLE")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.create(invalidRoleRequest))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Vai trò không tồn tại");
        }

        @Test
        @DisplayName("create - SALES STAFF maps to SALES role")
        void createUser_SalesStaffRole_MapsToSales() {
            UserRequest salesStaffRequest = UserRequest.builder()
                    .email("staff@example.com")
                    .password("Pass123@")
                    .fullName("Staff User")
                    .role("SALES STAFF")
                    .build();

            when(userRepository.existsByEmail(salesStaffRequest.getEmail())).thenReturn(false);
            when(userMapper.toEntity(salesStaffRequest)).thenReturn(testUser);
            when(passwordEncoder.encode(any())).thenReturn("hashed");
            when(userRepository.save(any(User.class))).thenReturn(testUser);
            when(roleRepository.findByName("SALES")).thenReturn(Optional.of(testRole));
            when(userRoleRepository.save(any(UserRole.class))).thenReturn(testUserRole);
            when(userMapper.toResponse(testUser)).thenReturn(testUserResponse);
            when(userRoleRepository.findByUserId(testUser.getId())).thenReturn(List.of(testUserRole));

            userService.create(salesStaffRequest);

            verify(roleRepository).findByName("SALES");
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // User Retrieval
    // ════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("User Retrieval")
    class UserRetrieval {

        @Test
        @DisplayName("getById - Existing user returns UserResponse")
        void getById_ExistingUser() {
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
            when(userMapper.toResponse(testUser)).thenReturn(testUserResponse);

            UserResponse result = userService.getById(testUser.getId());

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(testUser.getId());
            assertThat(result.getEmail()).isEqualTo(testUser.getEmail());
        }

        @Test
        @DisplayName("getById - Non-existent user throws AppException")
        void getById_NonExistent_ThrowsException() {
            UUID fakeId = UUID.randomUUID();
            when(userRepository.findById(fakeId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getById(fakeId))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("getByEmail - Existing user returns UserResponse")
        void getByEmail_ExistingUser() {
            when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
            when(userMapper.toResponse(testUser)).thenReturn(testUserResponse);

            UserResponse result = userService.getByEmail(testUser.getEmail());

            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo(testUser.getEmail());
        }

        @Test
        @DisplayName("getAll - Paginated returns PageResponse")
        void getAll_Paginated() {
            Page<User> page = new PageImpl<>(List.of(testUser), PageRequest.of(0, 10), 1);
            when(userRepository.findAll(any(PageRequest.class))).thenReturn(page);
            when(userMapper.toResponse(testUser)).thenReturn(testUserResponse);
            when(userRoleRepository.findByUserId(testUser.getId())).thenReturn(List.of(testUserRole));

            PageResponse<UserResponse> result = userService.getAll(0, 10);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getRole()).isEqualTo("SALES");
        }

        @Test
        @DisplayName("getAll - Page out of range returns empty page")
        void getAll_PageOutOfRange() {
            Page<User> emptyPage = new PageImpl<>(List.of(), PageRequest.of(99, 10), 0);
            when(userRepository.findAll(any(PageRequest.class))).thenReturn(emptyPage);

            PageResponse<UserResponse> result = userService.getAll(99, 10);

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isEqualTo(0);
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // User Update
    // ════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("User Update")
    class UserUpdate {

        @Test
        @DisplayName("update - Success updates fields")
        void updateUser_Success() {
            UserRequest updateRequest = UserRequest.builder()
                    .fullName("Updated Name")
                    .role("OPERATIONS")
                    .build();

            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
            when(userRoleRepository.findByUserId(testUser.getId())).thenReturn(List.of(testUserRole));
            when(userRepository.save(any(User.class))).thenReturn(testUser);
            when(userMapper.toResponse(testUser)).thenReturn(testUserResponse);
            when(roleRepository.findByName("OPERATIONS")).thenReturn(Optional.of(testRole));
            when(userRoleRepository.save(any(UserRole.class))).thenReturn(testUserRole);

            UserResponse result = userService.update(testUser.getId(), updateRequest);

            assertThat(result).isNotNull();
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("update - Non-existent user throws AppException")
        void updateUser_NonExistent_ThrowsException() {
            UUID fakeId = UUID.randomUUID();
            when(userRepository.findById(fakeId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.update(fakeId, testUserRequest))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("update - Status ACTIVE clears deletedAt")
        void updateUser_StatusActive_ClearsDeletedAt() {
            testUser.setDeletedAt(OffsetDateTime.now());

            UserRequest activateRequest = UserRequest.builder()
                    .status("ACTIVE")
                    .build();

            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
            when(userRoleRepository.findByUserId(testUser.getId())).thenReturn(List.of(testUserRole));
            when(userRepository.save(any(User.class))).thenReturn(testUser);
            when(userMapper.toResponse(testUser)).thenReturn(testUserResponse);

            userService.update(testUser.getId(), activateRequest);

            assertThat(testUser.getDeletedAt()).isNull();
            assertThat(testUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("changePassword - Success encodes and saves")
        void changePassword_Success() {
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("OldPass123@", "hashedPassword")).thenReturn(true);
            when(passwordEncoder.encode("NewPass123@")).thenReturn("newHashedPassword");
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            userService.changePassword(testUser.getId(), "OldPass123@", "NewPass123@");

            verify(passwordEncoder).encode("NewPass123@");
            verify(userRepository).save(testUser);
        }

        @Test
        @DisplayName("changePassword - Wrong old password throws IllegalArgumentException")
        void changePassword_WrongOldPassword_ThrowsException() {
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("WrongPassword", "hashedPassword")).thenReturn(false);

            assertThatThrownBy(() -> userService.changePassword(testUser.getId(), "WrongPassword", "NewPass123@"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Mật khẩu cũ không đúng");
        }

        @Test
        @DisplayName("changePassword - User not found throws AppException")
        void changePassword_UserNotFound_ThrowsException() {
            UUID fakeId = UUID.randomUUID();
            when(userRepository.findById(fakeId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.changePassword(fakeId, "Old", "New"))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // User Deletion
    // ════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("User Deletion")
    class UserDeletion {

        @Test
        @DisplayName("delete - Success soft deletes user")
        void deleteUser_Success() {
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            userService.delete(testUser.getId());

            assertThat(testUser.getStatus()).isEqualTo(UserStatus.INACTIVE);
            assertThat(testUser.getDeletedAt()).isNotNull();
            verify(userRepository).save(testUser);
        }

        @Test
        @DisplayName("delete - Non-existent user throws AppException")
        void deleteUser_NonExistent_ThrowsException() {
            UUID fakeId = UUID.randomUUID();
            when(userRepository.findById(fakeId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.delete(fakeId))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Profile Management
    // ════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Profile Management")
    class ProfileManagement {

        @Test
        @DisplayName("getMyProfile - Success returns UserProfileResponse")
        void getMyProfile_Success() {
            when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
            when(userRoleRepository.findByUserId(testUser.getId())).thenReturn(List.of(testUserRole));

            UserProfileResponse result = userService.getMyProfile(testUser.getEmail());

            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo(testUser.getEmail());
            assertThat(result.getFullName()).isEqualTo(testUser.getFullName());
            assertThat(result.getRole()).isEqualTo("SALES");
        }

        @Test
        @DisplayName("getMyProfile - Non-existent user throws AppException")
        void getMyProfile_UserNotFound_ThrowsException() {
            when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getMyProfile("nonexistent@example.com"))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("updateMyProfile - Success updates fullName and phone")
        void updateMyProfile_Success() {
            UpdateProfileRequest updateRequest = UpdateProfileRequest.builder()
                    .fullName("New Full Name")
                    .phone("0909999999")
                    .build();

            when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
            when(userRoleRepository.findByUserId(testUser.getId())).thenReturn(List.of(testUserRole));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            UserProfileResponse result = userService.updateMyProfile(testUser.getEmail(), updateRequest);

            assertThat(result).isNotNull();
            verify(userRepository).save(testUser);
        }

        @Test
        @DisplayName("updateMyProfile - Empty phone sets null")
        void updateMyProfile_EmptyPhone_SetsNull() {
            UpdateProfileRequest updateRequest = UpdateProfileRequest.builder()
                    .phone("")
                    .build();

            when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
            when(userRoleRepository.findByUserId(testUser.getId())).thenReturn(List.of(testUserRole));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            userService.updateMyProfile(testUser.getEmail(), updateRequest);

            assertThat(testUser.getPhone()).isNull();
        }

        @Test
        @DisplayName("changePassword - Profile success changes password")
        void changePassword_Profile_Success() {
            when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("OldPass123@", "hashedPassword")).thenReturn(true);
            when(passwordEncoder.encode("NewPass123@")).thenReturn("newHashed");
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            userService.changePassword(testUser.getEmail(), "OldPass123@", "NewPass123@");

            verify(passwordEncoder).encode("NewPass123@");
            verify(userRepository).save(testUser);
        }

        @Test
        @DisplayName("changePassword - Profile wrong password throws IllegalArgumentException")
        void changePassword_Profile_WrongPassword_ThrowsException() {
            when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("WrongPassword", "hashedPassword")).thenReturn(false);

            assertThatThrownBy(() -> userService.changePassword(testUser.getEmail(), "WrongPassword", "NewPass123@"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Mật khẩu cũ không đúng");
        }

        @Test
        @DisplayName("findUserIdByEmail - Success returns UUID")
        void findUserIdByEmail_Success() {
            when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));

            UUID result = userService.findUserIdByEmail(testUser.getEmail());

            assertThat(result).isEqualTo(testUser.getId());
        }

        @Test
        @DisplayName("findUserIdByEmail - Not found throws IllegalArgumentException")
        void findUserIdByEmail_NotFound_ThrowsException() {
            when(userRepository.findByEmail("notfound@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.findUserIdByEmail("notfound@example.com"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("User not found");
        }

        @Test
        @DisplayName("getProfileById - Success returns profile")
        void getProfileById_Success() {
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
            when(userRoleRepository.findByUserId(testUser.getId())).thenReturn(List.of(testUserRole));

            UserProfileResponse result = userService.getProfileById(testUser.getId());

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(testUser.getId());
        }

        @Test
        @DisplayName("getProfileById - Non-existent throws AppException")
        void getProfileById_NonExistent_ThrowsException() {
            UUID fakeId = UUID.randomUUID();
            when(userRepository.findById(fakeId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getProfileById(fakeId))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Password Validation
    // ════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Password Validation")
    class PasswordValidation {

        @Test
        @DisplayName("isValidPasswordFormat - Valid passwords return true")
        void isValidPasswordFormat_Valid() {
            assertThat(userService.isValidPasswordFormat("Password123@")).isTrue();
            assertThat(userService.isValidPasswordFormat("Abcdefgh1!")).isTrue();
            assertThat(userService.isValidPasswordFormat("MyPass99#")).isTrue();
        }

        @Test
        @DisplayName("isValidPasswordFormat - Weak passwords return false")
        void isValidPasswordFormat_Weak() {
            assertThat(userService.isValidPasswordFormat("short")).isFalse();
            assertThat(userService.isValidPasswordFormat("nodigits!")).isFalse();
            assertThat(userService.isValidPasswordFormat("NoSpecial1")).isFalse();
            assertThat(userService.isValidPasswordFormat("noupcase1!")).isFalse();
            assertThat(userService.isValidPasswordFormat("NOLOWERCASE1!")).isFalse();
            assertThat(userService.isValidPasswordFormat(null)).isFalse();
            assertThat(userService.isValidPasswordFormat("")).isFalse();
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Cancel Invite
    // ════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Cancel Invite")
    class CancelInviteTests {

        @Test
        @DisplayName("cancelInvite(userId) - user not found throws USER_NOT_FOUND")
        void cancelInvite_userNotFound() {
            UUID fake = UUID.randomUUID();
            when(userRepository.findById(fake)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.cancelInvite(fake))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("cancelInvite(userId) - ACTIVE user cannot be cancelled")
        void cancelInvite_userActiveRejected() {
            testUser.setStatus(UserStatus.ACTIVE);
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

            assertThatThrownBy(() -> userService.cancelInvite(testUser.getId()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Chỉ có thể hủy lời mời");
        }

        @Test
        @DisplayName("cancelInvite(userId) - INACTIVE with deletedAt cannot be cancelled")
        void cancelInvite_userDeletedAtRejected() {
            testUser.setStatus(UserStatus.INACTIVE);
            testUser.setDeletedAt(OffsetDateTime.now());
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

            assertThatThrownBy(() -> userService.cancelInvite(testUser.getId()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Chỉ có thể hủy lời mời");
        }

        @Test
        @DisplayName("cancelInvite(userId) - INACTIVE happy: cancels tokens, removes roles, deletes user")
        void cancelInvite_happy() {
            testUser.setStatus(UserStatus.INACTIVE);
            testUser.setDeletedAt(null);
            UserInviteToken pending = UserInviteToken.builder()
                    .id(UUID.randomUUID())
                    .email(testUser.getEmail())
                    .roleName("SALES")
                    .token("tok-1")
                    .expiresAt(OffsetDateTime.now().plusMinutes(15))
                    .status("PENDING")
                    .build();

            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
            when(userInviteTokenRepository.findAll()).thenReturn(List.of(pending));
            when(userRoleRepository.findByUserId(testUser.getId())).thenReturn(List.of(testUserRole));
            when(userInviteTokenRepository.save(any(UserInviteToken.class))).thenAnswer(i -> i.getArgument(0));

            userService.cancelInvite(testUser.getId());

            assertThat(pending.getStatus()).isEqualTo("CANCELLED");
            verify(userInviteTokenRepository).save(pending);
            verify(userRoleRepository).deleteAll(List.of(testUserRole));
            verify(userRepository).delete(testUser);
        }

        @Test
        @DisplayName("cancelInvite(userId) - skips already-used tokens")
        void cancelInvite_skipsUsedTokens() {
            testUser.setStatus(UserStatus.INACTIVE);
            UserInviteToken used = UserInviteToken.builder()
                    .id(UUID.randomUUID())
                    .email(testUser.getEmail())
                    .roleName("SALES")
                    .token("tok-used")
                    .expiresAt(OffsetDateTime.now().plusMinutes(15))
                    .usedAt(OffsetDateTime.now().minusMinutes(1))
                    .status("ACCEPTED")
                    .build();

            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
            when(userInviteTokenRepository.findAll()).thenReturn(List.of(used));
            when(userRoleRepository.findByUserId(testUser.getId())).thenReturn(List.of());

            userService.cancelInvite(testUser.getId());

            verify(userInviteTokenRepository, never()).save(any(UserInviteToken.class));
            verify(userRepository).delete(testUser);
        }

        @Test
        @DisplayName("cancelInviteByTokenId - token not found throws")
        void cancelInviteByTokenId_notFound() {
            UUID fake = UUID.randomUUID();
            when(userInviteTokenRepository.findById(fake)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.cancelInviteByTokenId(fake))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Không tìm thấy");
        }

        @Test
        @DisplayName("cancelInviteByTokenId - already accepted token throws")
        void cancelInviteByTokenId_alreadyUsed() {
            UserInviteToken used = UserInviteToken.builder()
                    .id(UUID.randomUUID())
                    .email("x@y.vn")
                    .token("t")
                    .expiresAt(OffsetDateTime.now().plusMinutes(15))
                    .usedAt(OffsetDateTime.now().minusMinutes(1))
                    .status("ACCEPTED")
                    .build();
            when(userInviteTokenRepository.findById(used.getId())).thenReturn(Optional.of(used));

            assertThatThrownBy(() -> userService.cancelInviteByTokenId(used.getId()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("đã được chấp nhận");
        }

        @Test
        @DisplayName("cancelInviteByTokenId - happy: cancels token and removes the linked INACTIVE user")
        void cancelInviteByTokenId_happy_removesInactiveUser() {
            UserInviteToken ok = UserInviteToken.builder()
                    .id(UUID.randomUUID())
                    .email(testUser.getEmail())
                    .roleName("SALES")
                    .token("tok")
                    .expiresAt(OffsetDateTime.now().plusMinutes(15))
                    .status("PENDING")
                    .build();
            testUser.setStatus(UserStatus.INACTIVE);
            testUser.setFullName("Chờ kích hoạt");

            when(userInviteTokenRepository.findById(ok.getId())).thenReturn(Optional.of(ok));
            when(userInviteTokenRepository.save(any(UserInviteToken.class))).thenAnswer(i -> i.getArgument(0));
            when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
            when(userRoleRepository.findByUserId(testUser.getId())).thenReturn(List.of(testUserRole));

            userService.cancelInviteByTokenId(ok.getId());

            assertThat(ok.getStatus()).isEqualTo("CANCELLED");
            verify(userInviteTokenRepository).save(ok);
            verify(userRoleRepository).deleteAll(List.of(testUserRole));
            verify(userRepository).delete(testUser);
        }

        @Test
        @DisplayName("cancelInviteByTokenId - keeps user if status != INACTIVE")
        void cancelInviteByTokenId_keepsUserIfActive() {
            UserInviteToken ok = UserInviteToken.builder()
                    .id(UUID.randomUUID())
                    .email(testUser.getEmail())
                    .roleName("SALES")
                    .token("tok")
                    .expiresAt(OffsetDateTime.now().plusMinutes(15))
                    .status("PENDING")
                    .build();
            testUser.setStatus(UserStatus.ACTIVE);

            when(userInviteTokenRepository.findById(ok.getId())).thenReturn(Optional.of(ok));
            when(userInviteTokenRepository.save(any(UserInviteToken.class))).thenAnswer(i -> i.getArgument(0));
            when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));

            userService.cancelInviteByTokenId(ok.getId());

            assertThat(ok.getStatus()).isEqualTo("CANCELLED");
            verify(userRepository, never()).delete(testUser);
        }
    }
}
