package fu.osms.audit.mapper;

import fu.osms.audit.dto.response.AuditLogResponse;
import fu.osms.audit.entity.AuditLog;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AuditLogMapper {

    @Mapping(target = "actorId", source = "actor.id")
    AuditLogResponse toResponse(AuditLog entity);
}
