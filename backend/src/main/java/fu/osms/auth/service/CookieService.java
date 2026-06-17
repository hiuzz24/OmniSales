package fu.osms.auth.service;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

public interface CookieService {
    public  void addRefreshTokenCookie(String refreshToken, HttpServletResponse response);
    public  void clearRefreshTokenCookie(HttpServletResponse response);
}
