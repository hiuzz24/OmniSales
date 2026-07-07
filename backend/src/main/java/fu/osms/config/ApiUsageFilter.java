package fu.osms.config;

import fu.osms.system.service.ApiMonitorService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiUsageFilter extends OncePerRequestFilter {

    private final ApiMonitorService apiMonitorService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();
        String method = request.getMethod();

        // Skip static resources or monitor/actuator endpoints to prevent loop
        if (uri.startsWith("/api/admin/monitor") || 
            uri.startsWith("/swagger-ui") || 
            uri.startsWith("/v3/api-docs") || 
            !uri.startsWith("/api")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Get identifier (Username/Email if authenticated, or Client IP)
        String identifier = getClientIdentifier(request);

        // Check Rate Limit
        if (apiMonitorService.isRateLimited(identifier, uri)) {
            log.warn("Rate limit exceeded for client: {} on endpoint: {}", identifier, uri);
            apiMonitorService.recordRequest(uri, method, false, 0);
            
            response.setStatus(429); // Too Many Requests
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"success\": false, \"message\": \"Too many requests. Please try again later.\"}");
            return;
        }

        long startTime = System.currentTimeMillis();
        boolean success = true;

        try {
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            success = false;
            throw e;
        } finally {
            long latency = System.currentTimeMillis() - startTime;
            
            // Determine success based on response status code
            int status = response.getStatus();
            if (status >= 400) {
                success = false;
            }

            // Extract matching pattern if available, fallback to URI
            String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            if (pattern == null) {
                pattern = uri;
            }

            apiMonitorService.recordRequest(pattern, method, success, latency);
        }
    }

    private String getClientIdentifier(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal().toString())) {
            return auth.getName(); // Returns email/username
        }
        
        // Fallback to IP address
        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr();
        }
        return ipAddress;
    }
}
