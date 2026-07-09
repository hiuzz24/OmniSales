package fu.osms.auth.repository;

import fu.osms.auth.entity.UserInviteToken;
import fu.osms.it.IntegrationTestBase;
import fu.osms.it.IntegrationTestCleanupHelper;
import fu.osms.it.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserInviteTokenRepositoryIT extends IntegrationTestBase {

    @Autowired UserInviteTokenRepository inviteRepo;
    @Autowired IntegrationTestCleanupHelper cleanup;

    @BeforeEach
    void setUp() {
        cleanup.truncate();
    }

    @Test
    void findByToken_returnsEmptyForUnknown() {
        Optional<UserInviteToken> got = inviteRepo.findByToken("UNKNOWN-" + UUID.randomUUID());
        assertThat(got).isEmpty();
    }

    @Test
    void saveAndFindByToken_roundtrip() {
        UserInviteToken t = new UserInviteToken();
        t.setToken("IT_TOKEN_" + TestDataFactory.uniqueSuffix());
        t.setEmail("invite+" + TestDataFactory.uniqueSuffix() + "@test.osms.vn");
        t.setRoleName("OPERATIONS");
        t.setExpiresAt(java.time.OffsetDateTime.now().plusDays(1));
        inviteRepo.save(t);

        Optional<UserInviteToken> got = inviteRepo.findByToken(t.getToken());
        assertThat(got).isPresent();
    }
}
