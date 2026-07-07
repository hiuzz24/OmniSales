package fu.osms.system.service.impl;

import fu.osms.audit.entity.AuditLog;
import fu.osms.audit.repository.AuditLogRepository;
import fu.osms.common.dto.PageResponse;
import fu.osms.system.dto.SystemLogResponse;
import fu.osms.system.entity.SystemLog;
import fu.osms.system.repository.SystemLogProjection;
import fu.osms.system.repository.SystemLogRepository;
import fu.osms.system.service.SystemLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemLogServiceImpl implements SystemLogService {

    private final SystemLogRepository systemLogRepository;
    private final AuditLogRepository auditLogRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SystemLogResponse> searchLogs(String level, String keyword, OffsetDateTime from, OffsetDateTime to, int page, int size) {
        Page<SystemLogProjection> projectionPage = systemLogRepository.searchSystemAndAuditLogs(
                level, keyword, from, to, PageRequest.of(page, size)
        );

        List<SystemLogResponse> content = projectionPage.getContent().stream()
                .map(p -> SystemLogResponse.builder()
                        .id(p.getId())
                        .type(p.getType())
                        .message(p.getMessage())
                        .user(p.getUser())
                        .ip(p.getIp())
                        .details(p.getDetails())
                        .timestamp(p.getTimestamp() != null ? OffsetDateTime.ofInstant(p.getTimestamp(), java.time.ZoneOffset.UTC) : null)
                        .build())
                .toList();

        return PageResponse.<SystemLogResponse>builder()
                .content(content)
                .page(projectionPage.getNumber())
                .size(projectionPage.getSize())
                .totalElements(projectionPage.getTotalElements())
                .totalPages(projectionPage.getTotalPages())
                .first(projectionPage.isFirst())
                .last(projectionPage.isLast())
                .build();
    }

    @Override
    @Transactional
    public SystemLogResponse updateLog(UUID id, SystemLogResponse updateDto) {
        // 1. Check system_logs
        SystemLog systemLog = systemLogRepository.findById(id).orElse(null);
        if (systemLog != null) {
            systemLog.setMessage(updateDto.getMessage());
            systemLog.setLevel(updateDto.getType());
            systemLog.setStackTrace(updateDto.getDetails());
            if (systemLog.getContext() == null) {
                systemLog.setContext(new HashMap<>());
            }
            systemLog.getContext().put("ip", updateDto.getIp());
            SystemLog saved = systemLogRepository.save(systemLog);
            
            return SystemLogResponse.builder()
                    .id(saved.getId())
                    .type(saved.getLevel())
                    .message(saved.getMessage())
                    .user("SYSTEM")
                    .ip(updateDto.getIp())
                    .details(saved.getStackTrace())
                    .timestamp(saved.getLoggedAt())
                    .build();
        }

        // 2. Check audit_logs
        AuditLog auditLog = auditLogRepository.findById(id).orElse(null);
        if (auditLog != null) {
            auditLog.setActorEmail(updateDto.getUser());
            
            // Map action LOGIN/LOGOUT
            String action = "LOGIN";
            if ("INFO".equalsIgnoreCase(updateDto.getType()) || "LOGOUT".equalsIgnoreCase(updateDto.getType())) {
                action = "LOGOUT";
            }
            auditLog.setAction(action);
            
            // Context changes
            if (auditLog.getChanges() == null) {
                auditLog.setChanges(new HashMap<>());
            }
            auditLog.getChanges().put("ip", updateDto.getIp());
            auditLog.getChanges().put("notes", updateDto.getDetails());
            
            AuditLog savedAudit = auditLogRepository.save(auditLog);
            
            return SystemLogResponse.builder()
                    .id(savedAudit.getId())
                    .type(savedAudit.getAction())
                    .message(updateDto.getMessage())
                    .user(savedAudit.getActorEmail())
                    .ip(updateDto.getIp())
                    .details(updateDto.getDetails())
                    .timestamp(savedAudit.getPerformedAt())
                    .build();
        }

        throw new IllegalArgumentException("Không tìm thấy dòng nhật ký có ID: " + id);
    }

    @Override
    @Transactional
    public void deleteLog(UUID id) {
        if (systemLogRepository.existsById(id)) {
            systemLogRepository.deleteById(id);
            log.info("Đã xóa log hệ thống id: {}", id);
            return;
        }

        if (auditLogRepository.existsById(id)) {
            auditLogRepository.deleteById(id);
            log.info("Đã xóa log hoạt động nghiệp vụ id: {}", id);
            return;
        }

        throw new IllegalArgumentException("Không tìm thấy dòng nhật ký có ID: " + id);
    }
}
