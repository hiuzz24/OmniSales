package fu.osms.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata for the generated OSMS internal API spec.
 * <p>
 * Declares the JWT Bearer scheme so Swagger UI can authorize requests against
 * protected endpoints. The static marketplace specs (Lazada / TikTok / Shopify /
 * RestCountries) are served from {@code classpath:static/openapi/*.yaml} and are
 * wired into the Swagger UI via {@code springdoc.swagger-ui.urls}.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI osmsOpenAPI() {
        final String securitySchemeName = "BearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("OSMS - OmniSales Management System API")
                        .description("""
                                Backend API của hệ thống OmniSales (quản lý bán hàng đa kênh: Shopify, Lazada, TikTok).

                                Đây là spec tự động sinh từ các controller. Các spec tĩnh của sàn (Lazada/TikTok/Shopify/RestCountries)
                                được hiển thị chung trong Swagger UI qua dropdown ở góc trên bên phải.
                                """)
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}
