package fu.osms.auth.mapper;

import fu.osms.auth.dto.request.UserRequest;
import fu.osms.auth.dto.response.UserResponse;
import fu.osms.auth.entity.User;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface UserMapper {

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

    @Mapping(target = "role", ignore = true)
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "warehouseId", source = "warehouse.id")
    @Mapping(target = "warehouseName", source = "warehouse.name")
    UserResponse toResponse(User user);

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
