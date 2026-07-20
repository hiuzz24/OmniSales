package fu.osms.auth.repository;

import fu.osms.auth.entity.Role;
import fu.osms.it.IntegrationTestBase;
import fu.osms.it.IntegrationTestCleanupHelper;
import fu.osms.it.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RoleRepositoryIT extends IntegrationTestBase {

    @Autowired RoleRepository roleRepo;
    @Autowired IntegrationTestCleanupHelper cleanup;

    @BeforeEach
    void setUp() {
        cleanup.truncate();
    }

    @Test
    void findByName_returnsExistingRole() {
        Role r = new Role();
        r.setName("IT_ROLE_" + TestDataFactory.uniqueSuffix());
        r.setDescription("Integration test role");
        roleRepo.save(r);

        Optional<Role> got = roleRepo.findByName(r.getName());
        assertThat(got).isPresent();
        assertThat(got.get().getId()).isEqualTo(r.getId());
    }

    @Test
    void existsByName_returnsTrue() {
        Role r = new Role();
        r.setName("IT_EXISTS_" + TestDataFactory.uniqueSuffix());
        roleRepo.save(r);

        assertThat(roleRepo.existsByName(r.getName())).isTrue();
        assertThat(roleRepo.existsByName("NOT-EXISTING-" + UUID.randomUUID())).isFalse();
    }

    @Test
    void findByName_returnsEmptyForUnknown() {
        Optional<Role> got = roleRepo.findByName("NOPE-" + UUID.randomUUID());
        assertThat(got).isEmpty();
    }
}
