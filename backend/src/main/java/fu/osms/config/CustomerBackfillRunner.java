package fu.osms.config;

import fu.osms.customer.service.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Backfill customers from orders on application start.
 *
 * Orders without a customer_id (only buyer_name + buyer_phone from
 * channel import) are linked back to a Customer entity so the
 * customer list page can show them.
 *
 * Runs after DatabaseMigration so the schema is up to date.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(10)
public class CustomerBackfillRunner implements ApplicationRunner {

    private final CustomerService customerService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            int updated = customerService.syncCustomersFromOrders();
            log.info("[CustomerBackfillRunner] Backfilled {} orders", updated);
        } catch (Exception e) {
            log.warn("[CustomerBackfillRunner] Failed to backfill orders: {}", e.getMessage(), e);
        }
    }
}
