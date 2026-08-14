package fu.osms.system.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.system.service.SystemSettingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SystemMaintenanceInterceptor implements HandlerInterceptor {

    private final SystemSettingService systemSettingService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!systemSettingService.getBoolean("maintenance_mode", false)
                || "OPTIONS".equalsIgnoreCase(request.getMethod())
                || isAllowedPath(request.getRequestURI())
                || isSystemAdmin()) {
            return true;
        }

        String message = systemSettingService.getString(
                "maintenance_message", "Hệ thống đang bảo trì. Vui lòng thử lại sau.");
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Map.of(
                "success", false,
                "message", message,
                "code", "MAINTENANCE_MODE"));
        return false;
    }

    private boolean isAllowedPath(String path) {
        return path.startsWith("/api/auth/")
                || path.startsWith("/api/admin/settings")
                || path.startsWith("/api/system/preferences")
                || path.startsWith("/actuator/");
    }

    private boolean isSystemAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_SYSTEM_ADMIN".equals(authority.getAuthority()));
    }
}
