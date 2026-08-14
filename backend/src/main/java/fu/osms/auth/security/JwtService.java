package fu.osms.auth.security;

import fu.osms.auth.entity.User;
import fu.osms.auth.repository.UserRepository;
import fu.osms.system.service.SystemSettingService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtService {
    private final UserRepository userRepository;
    private final SystemSettingService systemSettingService;

    @Value("${app.security.jwt.secret}")
    private String jwtSecret;

    @Value("${JWT_EXPIRATION_MS:86400000}")
    private long defaultAccessTokenExpirationMs;

    @Value("${JWT_REFRESH_EXPIRATION_MS:604800000}")
    private long defaultRefreshTokenExpirationMs;

    public long getAccessTokenExpirationMs() {
        long defaultMinutes = Math.max(defaultAccessTokenExpirationMs / 60_000L, 1L);
        return systemSettingService.getLong("access_token_expiration_minutes", defaultMinutes) * 60_000L;
    }

    public long getRefreshTokenExpirationMs() {
        long defaultDays = Math.max(defaultRefreshTokenExpirationMs / 86_400_000L, 1L);
        return systemSettingService.getLong("refresh_token_expiration_days", defaultDays) * 86_400_000L;
    }

    private SecretKey getSigningKey(){
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    /** Tạo token ngắn hạn dùng để xác thực các API request. */
    public String generateAccessToken(UserDetails userDetails){
        String role = userDetails.getAuthorities().stream().findFirst().map(GrantedAuthority::getAuthority).orElse("ROLE_USER");

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("Not found account"));

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getEmail())
                .claim("role", role)
                .claim("userId", user.getId())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + getAccessTokenExpirationMs()))
                .signWith(getSigningKey())
                .compact();
    }

    /** Tạo token dài hạn chỉ dùng để gia hạn phiên đăng nhập. */
    public String generateRefreshToken(UserDetails userDetails) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userDetails.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + getRefreshTokenExpirationMs()))
                .signWith(getSigningKey())
                .compact();
    }

    /** Phân tích và xác minh chữ ký JWT trước khi đọc claims. */
    public Claims extractClaim(String token){
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUsername(String token){
        return extractClaim(token).getSubject();
    }

    /** Kiểm tra chữ ký và thời hạn của token có hợp lệ hay không. */
    public boolean validateToken(String token){
        try{
            extractClaim(token);
            return true;
        }catch (JwtException e){
            return false;
        }
    }

}
