package fu.osms.auth.repository;

import fu.osms.auth.entity.User;
import fu.osms.auth.enums.UserStatus;
import fu.osms.it.IntegrationTestBase;
import fu.osms.it.IntegrationTestCleanupHelper;
import fu.osms.it.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserRepositoryIT extends IntegrationTestBase {

    @Autowired UserRepository userRepo;
    @Autowired TestDataFactory factory;
    @Autowired IntegrationTestCleanupHelper cleanup;

    @BeforeEach
    void setUp() {
        cleanup.truncate();
    }

    @Test
    void saveAndFindById_roundtrip() {
        User u = userRepo.save(User.builder()
                .email(TestDataFactory.uniqueEmail("user"))
                .passwordHash("hash")
                .fullName("IT User")
                .status(UserStatus.ACTIVE)
                .build());

        Optional<User> got = userRepo.findById(u.getId());
        assertThat(got).isPresent();
    }

    @Test
    void findByEmail_returnsExisting() {
        User u = userRepo.save(User.builder()
                .email(TestDataFactory.uniqueEmail("u"))
                .passwordHash("hash")
                .fullName("IT User")
                .status(UserStatus.ACTIVE)
                .build());

        Optional<User> got = userRepo.findByEmail(u.getEmail());
        assertThat(got).isPresent();
        assertThat(got.get().getId()).isEqualTo(u.getId());
    }

    @Test
    void existsByEmail_returnsTrueForExisting() {
        User u = userRepo.save(User.builder()
                .email(TestDataFactory.uniqueEmail("e"))
                .passwordHash("hash")
                .fullName("IT")
                .status(UserStatus.ACTIVE)
                .build());

        assertThat(userRepo.existsByEmail(u.getEmail())).isTrue();
        assertThat(userRepo.existsByEmail("nope-" + UUID.randomUUID() + "@test.osms.vn")).isFalse();
    }

    @Test
    void findActiveById_returnsActiveUser() {
        User u = userRepo.save(User.builder()
                .email(TestDataFactory.uniqueEmail("act"))
                .passwordHash("hash")
                .fullName("IT")
                .status(UserStatus.ACTIVE)
                .build());

        Optional<User> got = userRepo.findActiveById(u.getId());
        assertThat(got).isPresent();
    }

    @Test
    void factory_newInactiveUser_isNotActive() {
        User u = userRepo.save(factory.newInactiveUser());
        Optional<User> got = userRepo.findById(u.getId());
        assertThat(got).isPresent();
        assertThat(got.get().getStatus()).isEqualTo(UserStatus.INACTIVE);
    }
}
