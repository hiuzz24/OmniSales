package fu.osms.customer.repository;

import fu.osms.customer.entity.Customer;
import fu.osms.it.IntegrationTestBase;
import fu.osms.it.IntegrationTestCleanupHelper;
import fu.osms.it.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link CustomerRepository}.
 *
 * <p>Extends {@link IntegrationTestBase} which boots the full Spring context
 * with the isolated {@code osms_it} PostgreSQL database and mocks all
 * external services (Gmail, RabbitMQ, RestCountries API).</p>
 */
class CustomerRepositoryIT extends IntegrationTestBase {

    @Autowired CustomerRepository repository;
    @Autowired TestDataFactory factory;
    @Autowired IntegrationTestCleanupHelper cleanup;

    @BeforeEach
    void setUp() {
        cleanup.truncate();
    }

    @Test
    void saveAndFindById_roundtrip() {
        Customer saved = repository.save(factory.newCustomer());

        Optional<Customer> found = repository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo(saved.getEmail());
        assertThat(found.get().getCode()).startsWith("KH");
    }

    @Test
    void findByPhone_andEmail_returnsExisting() {
        Customer customer = factory.newCustomer();
        repository.save(customer);

        Optional<Customer> byPhone = repository.findByPhone(customer.getPhone());
        Optional<Customer> byEmail = repository.findByEmail(customer.getEmail());

        assertThat(byPhone).isPresent();
        assertThat(byEmail).isPresent();
        assertThat(byPhone.get().getId()).isEqualTo(byEmail.get().getId());
    }

    @Test
    void searchByKeyword_matchesCodeNamePhoneEmail() {
        Customer a = repository.save(Customer.builder()
                .fullName("Alice Wonderland")
                .phone("0981111111")
                .email("alice@test.com")
                .isActive(true)
                .build());
        Customer b = repository.save(Customer.builder()
                .fullName("Bob Builder")
                .phone("0982222222")
                .email("bob@test.com")
                .isActive(true)
                .build());

        Page<Customer> page = repository.findAllBySearchKeyword(
                "%alice%", PageRequest.of(0, 10));
        assertThat(page.getContent()).extracting(Customer::getId).contains(a.getId()).doesNotContain(b.getId());

        Page<Customer> phonePage = repository.findAllBySearchKeyword(
                "%098222%", PageRequest.of(0, 10));
        assertThat(phonePage.getContent()).extracting(Customer::getId).contains(b.getId());
    }

    @Test
    void findAllByIsActiveAndGender_filtersCorrectly() {
        Customer male = repository.save(Customer.builder()
                .fullName("Mr. Active")
                .gender("Nam")
                .email("m1@test.com")
                .isActive(true)
                .build());
        Customer inactiveMale = repository.save(Customer.builder()
                .fullName("Mr. Inactive")
                .gender("Nam")
                .email("m2@test.com")
                .isActive(false)
                .build());
        Customer activeFemale = repository.save(Customer.builder()
                .fullName("Ms. Active")
                .gender("Nữ")
                .email("f1@test.com")
                .isActive(true)
                .build());

        Page<Customer> page = repository.findAllByIsActiveAndGender(true, "Nam", PageRequest.of(0, 10));
        assertThat(page.getContent()).extracting(Customer::getId)
                .contains(male.getId())
                .doesNotContain(inactiveMale.getId(), activeFemale.getId());
    }

    @Test
    void countAll_andCountActive_returnExpectedTotals() {
        repository.save(factory.newCustomer());
        repository.save(factory.newCustomer());
        Customer inactive = factory.newCustomer();
        inactive.setIsActive(false);
        repository.save(inactive);

        assertThat(repository.countAll()).isEqualTo(3L);
        assertThat(repository.countActive()).isEqualTo(2L);
    }

    @Test
    void deleteById_removesCustomer() {
        UUID id = repository.save(factory.newCustomer()).getId();

        repository.deleteById(id);

        assertThat(repository.findById(id)).isEmpty();
    }

    @Test
    void sumTotalSpent_returnsZeroWhenNoOrders() {
        // No orders exist right after seeding → SUM returns 0
        BigDecimal total = repository.sumTotalSpent();
        assertThat(total).isEqualByComparingTo(BigDecimal.ZERO);
    }
}