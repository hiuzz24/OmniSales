package fu.osms.auth.repository;

import fu.osms.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE RefreshToken t SET t.revokedAt = CURRENT_TIMESTAMP WHERE t.user.id = :userId AND t.revokedAt IS NULL")
    int revokeAllByUserId(UUID userId);

    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < CURRENT_TIMESTAMP")
    int deleteExpiredTokens();
}
