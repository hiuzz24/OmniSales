package fu.osms.system.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class SystemWebMvcConfig implements WebMvcConfigurer {

    private final SystemMaintenanceInterceptor maintenanceInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(maintenanceInterceptor).addPathPatterns("/api/**");
    }
}
