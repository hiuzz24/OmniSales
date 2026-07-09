package fu.osms.auth.repository;

import fu.osms.auth.entity.RefreshToken;
import fu.osms.auth.entity.User;
import fu.osms.auth.enums.UserStatus;
import fu.osms.it.IntegrationTestBase;
import fu.osms.it.IntegrationTestCleanupHelper;
import fu.osms.it.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenRepositoryIT extends IntegrationTestBase {

    @Autowired RefreshTokenRepository refreshRepo;
    @Autowired UserRepository userRepo;
    @Autowired TestDataFactory factory;
    @Autowired IntegrationTestCleanupHelper cleanup;

    @BeforeEach
    void setUp() {
        cleanup.truncate();
    }

    @Test
    void findByTokenHash_returnsExisting() {
        User u = userRepo.save(User.builder()
                .email(TestDataFactory.uniqueEmail("rt"))
                .passwordHash("h")
                .fullName("RT")
                .status(UserStatus.ACTIVE)
                .build());
        String hash = "HASH-" + TestDataFactory.uniqueSuffix();
        RefreshToken t = new RefreshToken();
        t.setUser(u);
        t.setTokenHash(hash);
        t.setExpiresAt(OffsetDateTime.now().plusDays(1));
        refreshRepo.save(t);

        Optional<RefreshToken> got = refreshRepo.findByTokenHash(hash);
        assertThat(got).isPresent();
    }

    @Test
    void findByTokenHash_returnsEmptyForUnknown() {
        Optional<RefreshToken> got = refreshRepo.findByTokenHash("UNKNOWN-" + TestDataFactory.uniqueSuffix());
        assertThat(got).isEmpty();
    }

    @Test
    @Transactional
    void deleteExpiredTokens_returnsZeroOrMore() {
        int deleted = refreshRepo.deleteExpiredTokens();
        assertThat(deleted).isGreaterThanOrEqualTo(0);
    }

    @Test
    @Transactional
    void revokeAllByUserId_returnsCount() {
        int count = refreshRepo.revokeAllByUserId(java.util.UUID.randomUUID());
        assertThat(count).isGreaterThanOrEqualTo(0);
    }
}
