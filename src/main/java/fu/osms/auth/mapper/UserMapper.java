package fu.osms.auth.mapper;

import fu.osms.auth.dto.request.UserRequest;
import fu.osms.auth.dto.response.UserResponse;
import fu.osms.auth.entity.User;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface UserMapper {

    /**
     * Chuyển đổi UserRequest -> User entity (dùng khi tạo mới).
     * Bỏ qua passwordHash vì sẽ được mã hoá riêng trong service.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "emailVerifiedAt", ignore = true)
    @Mapping(target = "verificationToken", ignore = true)
    @Mapping(target = "failedLoginAttempts", ignore = true)
    @Mapping(target = "lockedUntil", ignore = true)
    User toEntity(UserRequest request);

    /**
     * Chuyển đổi User entity -> UserResponse DTO.
     */
    UserResponse toResponse(User user);

    /**
     * Cập nhật User entity từ UserRequest (dùng khi update).
     * Bỏ qua các trường không nên thay đổi từ request.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "emailVerifiedAt", ignore = true)
    @Mapping(target = "verificationToken", ignore = true)
    @Mapping(target = "failedLoginAttempts", ignore = true)
    @Mapping(target = "lockedUntil", ignore = true)
    void updateEntityFromRequest(UserRequest request, @MappingTarget User user);
}
