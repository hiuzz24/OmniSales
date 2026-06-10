package fu.osms.auth.security;

import fu.osms.auth.entity.User;
import fu.osms.auth.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
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

    @Value("${JWT_SECRET}")
    private String jwtSecret;

    @Getter
    @Value("${JWT_EXPIRATION_MS:120000}")
    private long accessTokenExpirationMs;

    @Value("${JWT_REFRESH_EXPIRATION_MS:604800000}")
    private long refreshTokenExpirationMs;

    private SecretKey getSigningKey(){
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

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
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    public String generateRefreshToken(UserDetails userDetails) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userDetails.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpirationMs))
                .signWith(getSigningKey())
                .compact();
    }

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

    public boolean validateToken(String token){
        try{
            extractClaim(token);
            return true;
        }catch (JwtException e){
            return false;
        }
    }

}
