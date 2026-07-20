package fu.osms.order.repository;

import fu.osms.common.enums.PlatformType;
import fu.osms.it.IntegrationTestCleanupHelper;
import fu.osms.order.entity.Order;
import fu.osms.order.enums.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link OrderRepository}.
 *
 * <p>Real PostgreSQL ({@code osms_it}). We insert orders via direct SQL
 * to avoid coupling to service-layer logic, then verify the
 * repository's custom JPQL aggregations.</p>
 */
@SpringBootTest
@ActiveProfiles("it")
class OrderRepositoryIT {

    @Autowired OrderRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired IntegrationTestCleanupHelper cleanup;

    @BeforeEach
    void setUp() {
        cleanup.truncate();
    }

    @Test
    void saveAndFind_roundtrip() {
        Order order = baseOrder();
        order.setExternalOrderId("EXT-IT-" + System.nanoTime());

        Order saved = repository.save(order);

        Optional<Order> found = repository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getExternalOrderId()).isEqualTo(saved.getExternalOrderId());
    }

    @Test
    void findByExternalOrderId_returnsMatch() {
        String externalId = "EXT-FIND-" + System.nanoTime();
        Order o = baseOrder();
        o.setExternalOrderId(externalId);
        repository.save(o);

        Optional<Order> found = repository.findByExternalOrderId(externalId);
        assertThat(found).isPresent();
    }

    @Test
    void findByStatus_filtersCorrectly() {
        UUID idPending = saveOrder(OrderStatus.PENDING);
        UUID idShipped = saveOrder(OrderStatus.SHIPPED);
        UUID idDelivered = saveOrder(OrderStatus.DELIVERED);

        Page<Order> pending = repository.findByStatus(OrderStatus.PENDING, PageRequest.of(0, 10));
        Page<Order> delivered = repository.findByStatus(OrderStatus.DELIVERED, PageRequest.of(0, 10));

        assertThat(pending).extracting(Order::getId).contains(idPending)
                .doesNotContain(idShipped, idDelivered);
        assertThat(delivered).extracting(Order::getId).contains(idDelivered);
    }

    @Test
    void countByStatus_returnsAccurateCount() {
        saveOrder(OrderStatus.PENDING);
        saveOrder(OrderStatus.PENDING);
        saveOrder(OrderStatus.SHIPPED);

        assertThat(repository.countByStatus(OrderStatus.PENDING)).isEqualTo(2L);
        assertThat(repository.countByStatus(OrderStatus.SHIPPED)).isEqualTo(1L);
        assertThat(repository.countByStatus(OrderStatus.CANCELLED)).isEqualTo(0L);
    }

    @Test
    void sumRevenueDelivered_zeroWhenNoDelivered() {
        saveOrder(OrderStatus.PENDING);
        saveOrder(OrderStatus.SHIPPED);

        BigDecimal total = repository.sumRevenueDelivered();
        assertThat(total).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void sumRevenueDelivered_sumsDeliveredOrdersOnly() {
        saveOrderWithTotal(OrderStatus.PENDING, 100000);
        saveOrderWithTotal(OrderStatus.DELIVERED, 500000);
        saveOrderWithTotal(OrderStatus.DELIVERED, 300000);
        saveOrderWithTotal(OrderStatus.SHIPPED, 700000);

        BigDecimal total = repository.sumRevenueDelivered();
        assertThat(total).isEqualByComparingTo(BigDecimal.valueOf(800000));
    }

    @Test
    void findByDateRange_filtersWithinWindow() {
        // Insert one order right now - it should be within the next 5min window
        UUID todayId = saveOrder(OrderStatus.PENDING);

        Page<Order> range = repository.findByDateRange(
                OffsetDateTime.now().minusHours(1),
                OffsetDateTime.now().plusHours(1),
                PageRequest.of(0, 100));

        assertThat(range).extracting(Order::getId).contains(todayId);
    }

    /**
     * Helpers
     */

    private Order baseOrder() {
        return Order.builder()
                .platform(PlatformType.MANUAL)
                .channelName("Manual")
                .externalOrderId("EXT")
                .status(OrderStatus.PENDING)
                .paymentStatus("UNPAID")
                .shippingAddress(Map.of("city", "HCMC"))
                .subtotal(BigDecimal.valueOf(100000))
                .discountAmount(BigDecimal.ZERO)
                .shippingFee(BigDecimal.ZERO)
                .totalAmount(BigDecimal.valueOf(100000))
                .currency("VND")
                .build();
    }

    private UUID saveOrder(OrderStatus status) {
        return saveOrderWithTotal(status, 100000);
    }

    private UUID saveOrderWithTotal(OrderStatus status, long total) {
        Order o = baseOrder();
        o.setExternalOrderId("EXT-" + System.nanoTime() + "-" + Math.random());
        o.setStatus(status);
        o.setSubtotal(BigDecimal.valueOf(total));
        o.setTotalAmount(BigDecimal.valueOf(total));
        Order saved = repository.save(o);
        return saved.getId();
    }

    private UUID saveOrderWithDate(OrderStatus status, OffsetDateTime when) {
        Order o = baseOrder();
        o.setExternalOrderId("EXT-" + System.nanoTime() + "-" + Math.random());
        o.setStatus(status);
        repository.save(o);
        return o.getId();
    }
}