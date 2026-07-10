package fu.osms.order.repository;

import fu.osms.catalog.repository.CategoryRepository;
import fu.osms.catalog.repository.ProductRepository;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.it.IntegrationTestBase;
import fu.osms.it.IntegrationTestCleanupHelper;
import fu.osms.it.TestDataFactory;
import fu.osms.order.entity.Order;
import fu.osms.order.entity.OrderItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class OrderItemRepositoryIT extends IntegrationTestBase {

    @Autowired OrderItemRepository orderItemRepo;
    @Autowired OrderRepository orderRepo;
    @Autowired CategoryRepository categoryRepo;
    @Autowired ProductRepository productRepo;
    @Autowired ProductVariantRepository variantRepo;
    @Autowired TestDataFactory factory;
    @Autowired IntegrationTestCleanupHelper cleanup;

    @BeforeEach
    void setUp() {
        cleanup.truncate();
    }

    @Test
    void findByOrderId_returnsAllItemsOfOrder() {
        Order order = orderRepo.save(factory.newOrder());
        OrderItem item1 = orderItemRepo.save(factory.newOrderItem(order, 2, 50000));
        OrderItem item2 = orderItemRepo.save(factory.newOrderItem(order, 3, 30000));

        List<OrderItem> items = orderItemRepo.findByOrderId(order.getId());

        assertThat(items).extracting(OrderItem::getId)
                .contains(item1.getId(), item2.getId());
    }

    @Test
    void findByOrderIdIn_returnsMultipleOrdersItems() {
        Order o1 = orderRepo.save(factory.newOrder());
        Order o2 = orderRepo.save(factory.newOrder());
        OrderItem i1 = orderItemRepo.save(factory.newOrderItem(o1, 1, 1000));
        OrderItem i2 = orderItemRepo.save(factory.newOrderItem(o2, 2, 2000));

        List<OrderItem> got = orderItemRepo.findByOrderIdIn(List.of(o1.getId(), o2.getId()));

        assertThat(got).extracting(OrderItem::getId)
                .contains(i1.getId(), i2.getId());
    }

    @Test
    @Transactional
    void deleteByOrderId_removesAllItems() {
        Order order = orderRepo.save(factory.newOrder());
        orderItemRepo.save(factory.newOrderItem(order, 1, 1000));
        orderItemRepo.save(factory.newOrderItem(order, 2, 2000));

        orderItemRepo.deleteByOrderId(order.getId());
        orderItemRepo.flush();

        assertThat(orderItemRepo.findByOrderId(order.getId())).isEmpty();
    }

    @Test
    @Transactional
    void findVariantIdsWithOrders_returnsMatchedVariants() {
        var category = categoryRepo.save(factory.newCategory());
        var product = productRepo.save(factory.newProduct(category.getId()));
        var variant = variantRepo.save(factory.newVariant(product.getId()));

        Order order = orderRepo.save(factory.newOrder());
        OrderItem item = factory.newOrderItem(order, 1, 1000);
        item.setVariant(variant);
        orderItemRepo.save(item);

        List<UUID> got = orderItemRepo.findVariantIdsWithOrders(List.of(variant.getId()));
        assertThat(got).contains(variant.getId());
    }

    @Test
    void findByOrderId_emptyForNewOrder() {
        Order order = orderRepo.save(factory.newOrder());
        List<OrderItem> items = orderItemRepo.findByOrderId(order.getId());
        assertThat(items).isEmpty();
    }
}
