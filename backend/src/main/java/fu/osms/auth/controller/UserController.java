package fu.osms.auth.controller;

import fu.osms.auth.dto.request.ChangeMyPasswordRequest;
import fu.osms.auth.dto.request.UpdateProfileRequest;
import fu.osms.auth.dto.request.UserRequest;
import fu.osms.auth.dto.response.UserProfileResponse;
import fu.osms.auth.dto.response.UserResponse;
import fu.osms.auth.dto.response.UserInviteResponse;
import fu.osms.auth.service.UserService;
import fu.osms.common.dto.ApiResponse;
import fu.osms.common.dto.PageResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // ══════════════════════════════════════════════════════════════════════════
    // CRUD
    // ══════════════════════════════════════════════════════════════════════════

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> create(@Valid @RequestBody UserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User created successfully", userService.create(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(userService.getById(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(userService.getAll(page, size)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UserRequest request) {
        return ResponseEntity.ok(ApiResponse.success("User updated successfully", userService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        userService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("User deleted successfully", null));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Profile (current user)
    // ══════════════════════════════════════════════════════════════════════════

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile() {
        String email = getCurrentEmail();
        log.info("Fetching profile for: {}", email);
        return ResponseEntity.ok(ApiResponse.success(userService.getMyProfile(email)));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateMyProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        String email = getCurrentEmail();
        log.info("Updating profile for: {}", email);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật hồ sơ thành công",
                userService.updateMyProfile(email, request)));
    }

    @PostMapping("/me/change-password")
    public ResponseEntity<ApiResponse<Void>> changeMyPassword(
            @Valid @RequestBody ChangeMyPasswordRequest request) {
        String email = getCurrentEmail();
        log.info("Changing password for: {}", email);
        userService.changePassword(email, request.getOldPassword(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success("Đổi mật khẩu thành công", null));
    }

    @PostMapping("/{id}/cancel-invite")
    public ResponseEntity<ApiResponse<Void>> cancelInvite(@PathVariable UUID id) {
        log.info("Cancelling invitation for user ID: {}", id);
        userService.cancelInvite(id);
        return ResponseEntity.ok(ApiResponse.success("Hủy lời mời thành công", null));
    }

    @GetMapping("/invitations")
    @PreAuthorize("hasAnyRole('OWNER', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<List<UserInviteResponse>>> getInvitations() {
        log.info("Fetching all member invitations");
        return ResponseEntity.ok(ApiResponse.success(userService.getInvitations()));
    }

    @PostMapping("/invitations/{id}/cancel")
    @PreAuthorize("hasAnyRole('OWNER', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> cancelInviteByToken(@PathVariable UUID id) {
        log.info("Cancelling invitation for token ID: {}", id);
        userService.cancelInviteByTokenId(id);
        return ResponseEntity.ok(ApiResponse.success("Hủy lời mời thành công", null));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String getCurrentEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user found");
        }
        return auth.getName();
    }
}
