package fu.osms.it;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Mint a JWT directly without going through /api/auth/login.
 *
 * <p>Useful in tests where the JWT filter needs to be exercised but the
 * login flow itself is not the focus. Reads the secret from the active
 * Spring context so tests stay in sync with {@code application-it.yaml}.</p>
 */
@Component
public class JwtTestUtils {

    @Value("${app.security.jwt.secret}")
    private String secret;

    public String generate(String email, String role, UUID userId) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(email)
                .claims(Map.of(
                        "role", role,
                        "userId", userId.toString()))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600_000L))
                .signWith(key)
                .compact();
    }

    public String generateExpired(String email, String role, UUID userId) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(email)
                .claims(Map.of(
                        "role", role,
                        "userId", userId.toString()))
                .issuedAt(new Date(System.currentTimeMillis() - 7200_000L))
                .expiration(new Date(System.currentTimeMillis() - 3600_000L))
                .signWith(key)
                .compact();
    }
}