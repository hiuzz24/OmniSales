package fu.osms.auth.service;

import fu.osms.auth.dto.request.UpdateProfileRequest;
import fu.osms.auth.dto.request.UserRequest;
import fu.osms.auth.dto.response.UserProfileResponse;
import fu.osms.auth.dto.response.UserResponse;
import fu.osms.common.dto.PageResponse;

import java.util.UUID;

public interface UserService {

    // ── CRUD ────────────────────────────────────────────────────────────────
    UserResponse create(UserRequest request);

    UserResponse getById(UUID id);

    UserResponse getByEmail(String email);

    PageResponse<UserResponse> getAll(int page, int size);

    UserResponse update(UUID id, UserRequest request);

    void changePassword(UUID id, String oldPassword, String newPassword);

    void delete(UUID id);

    // ── Profile (current user) ────────────────────────────────────────────
    UserProfileResponse getMyProfile(String email);

    UserProfileResponse updateMyProfile(String email, UpdateProfileRequest request);

    UserProfileResponse getProfileById(UUID userId);

    void changePassword(String email, String oldPassword, String newPassword);
}
