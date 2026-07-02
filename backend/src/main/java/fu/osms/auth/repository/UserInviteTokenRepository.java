package fu.osms.auth.repository;

import fu.osms.auth.entity.UserInviteToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserInviteTokenRepository extends JpaRepository<UserInviteToken, UUID> {
    Optional<UserInviteToken> findByToken(String token);
}
