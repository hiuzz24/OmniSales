package fu.osms.auth.repository;

import fu.osms.auth.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByToken(String token);

    @Query("SELECT p FROM PasswordResetToken p WHERE p.user.id = :userId " +
           "AND p.usedAt IS NULL AND p.expiresAt > :now")
    Optional<PasswordResetToken> findValidTokenByUserId(@Param("userId") UUID userId,
                                                         @Param("now") OffsetDateTime now);

    @Modifying
    @Query("DELETE FROM PasswordResetToken p WHERE p.expiresAt < :now AND p.usedAt IS NULL")
    int deleteExpiredTokens(@Param("now") OffsetDateTime now);
}
