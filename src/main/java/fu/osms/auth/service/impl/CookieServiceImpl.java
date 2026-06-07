package fu.osms.auth.service.impl;

import fu.osms.auth.service.CookieService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import java.time.Duration;

@Service
public class CookieServiceImpl implements CookieService {
    @Override
    public void addRefreshTokenCookie(String refreshToken, HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie
                .from("refreshToken",refreshToken)
                .httpOnly(true)
                .path("/api/auth/refreshToken")
                .sameSite("Strict")
                .maxAge(Duration.ofDays(7))
                .secure(true)
                .build();
        response.addHeader("Set-Cookie",cookie.toString());
    }
}
