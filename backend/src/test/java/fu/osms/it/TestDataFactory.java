package fu.osms.it;

import fu.osms.auth.entity.User;
import fu.osms.auth.enums.UserStatus;
import fu.osms.catalog.dto.request.ProductVariantRequest;
import fu.osms.catalog.entity.Category;
import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.enums.ProductStatus;
import fu.osms.channel.entity.Channel;
import fu.osms.common.enums.PlatformType;
import fu.osms.customer.dto.request.CustomerRequest;
import fu.osms.customer.entity.Customer;
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.inventory.entity.Supplier;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.order.entity.Order;
import fu.osms.order.entity.OrderItem;
import fu.osms.order.enums.OrderStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Builder helpers for common test entities and request payloads.
 *
 * <p>Provides deterministic-but-unique values (timestamp-based) so two
 * tests never collide on unique constraints (email, phone, SKU, etc.).</p>
 */
@Component
public class TestDataFactory {

    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 1_000_000);

    private final PasswordEncoder passwordEncoder;

    public TestDataFactory(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    /** Returns a unique 8-digit suffix safe for use in unique fields. */
    public static String uniqueSuffix() {
        return String.valueOf(System.nanoTime() % 1_000_000_000L);
    }

    public static String uniqueEmail(String prefix) {
        return prefix + "+" + uniqueSuffix() + "@test.osms.vn";
    }

    public static String uniquePhone() {
        return "098" + String.format("%07d", Math.abs(uniqueSuffix().hashCode()) % 10_000_000);
    }

    public static UUID newId() {
        return UUID.randomUUID();
    }

    // ─── Customer ────────────────────────────────────────────────────────

    /** Builds a Customer entity suitable for direct repository.save(...). */
    public Customer newCustomer() {
        String suffix = uniqueSuffix();
        Map<String, Object> address = new HashMap<>();
        address.put("street", "123 Test Street");
        address.put("city", "Ho Chi Minh");
        return Customer.builder()
                .fullName("Customer " + suffix)
                .gender("Nam")
                .birth(LocalDate.of(1990, 1, 1))
                .phone(uniquePhone())
                .email(uniqueEmail("cust"))
                .address(address)
                .notes("created by test")
                .isActive(true)
                .build();
    }

    /** Builds a CustomerRequest for HTTP POST /api/customers. */
    public CustomerRequest customerRequest() {
        return CustomerRequest.builder()
                .fullName("Customer " + uniqueSuffix())
                .gender("Nam")
                .phone(uniquePhone())
                .email(uniqueEmail("cust"))
                .build();
    }

    // ─── User ────────────────────────────────────────────────────────────

    public User newInactiveUser() {
        return User.builder()
                .email(uniqueEmail("inactive"))
                .passwordHash(passwordEncoder.encode("11111111"))
                .fullName("Inactive User")
                .status(UserStatus.INACTIVE)
                .build();
    }

    // ─── Warehouse / Supplier ───────────────────────────────────────────

    public Warehouse newWarehouse() {
        return Warehouse.builder()
                .name("Test Warehouse " + uniqueSuffix())
                .address("Test Address")
                .isActive(true)
                .build();
    }

    public Supplier newSupplier() {
        return Supplier.builder()
                .supplierCode("SUP" + uniqueSuffix())
                .name("Test Supplier " + uniqueSuffix())
                .email(uniqueEmail("sup"))
                .phone(uniquePhone())
                .build();
    }

    // ─── Catalog ────────────────────────────────────────────────────────

    public Category newCategory() {
        return Category.builder()
                .name("Category " + uniqueSuffix())
                .slug("slug-" + uniqueSuffix())
                .build();
    }

    public Product newProduct(UUID categoryId) {
        return Product.builder()
                .category(Category.builder().id(categoryId).build())
                .sku("SKU-IT-" + uniqueSuffix())
                .name("Product " + uniqueSuffix())
                .status(ProductStatus.ACTIVE)
                .attributes(new HashMap<>())
                .build();
    }

    public ProductVariant newVariant(UUID productId) {
        return ProductVariant.builder()
                .product(Product.builder().id(productId).build())
                .sku("VAR-IT-" + uniqueSuffix())
                .name("Variant " + uniqueSuffix())
                .price(BigDecimal.valueOf(100000))
                .isActive(true)
                .optionValues(new HashMap<>())
                .build();
    }

    public ProductVariantRequest newVariantRequest() {
        return ProductVariantRequest.builder()
                .sku("VAR-IT-" + uniqueSuffix())
                .name("Variant " + uniqueSuffix())
                .isActive(true)
                .build();
    }

    // ─── Inventory ──────────────────────────────────────────────────────

    public InventoryItem newInventoryItem(UUID warehouseId, UUID variantId, int qty) {
        return InventoryItem.builder()
                .warehouse(Warehouse.builder().id(warehouseId).build())
                .variant(ProductVariant.builder().id(variantId).build())
                .quantityOnHand(qty)
                .availableQuantity(qty)
                .reservedQuantity(0)
                .lowStockThreshold(5)
                .build();
    }

    // ─── Channel ────────────────────────────────────────────────────────

    public Channel newChannel() {
        return Channel.builder()
                .platform(PlatformType.SHOPIFY)
                .displayName("Test Shop " + uniqueSuffix())
                .status("CONNECTED")
                .syncEnabled(true)
                .metadata(new HashMap<>())
                .build();
    }

    // ─── Order ──────────────────────────────────────────────────────────

    public Order newOrder() {
        Map<String, Object> addr = new HashMap<>();
        addr.put("city", "HCMC");
        return Order.builder()
                .platform(PlatformType.MANUAL)
                .channelName("Manual")
                .externalOrderId("EXT-" + uniqueSuffix())
                .status(OrderStatus.PENDING)
                .paymentStatus("UNPAID")
                .shippingAddress(addr)
                .subtotal(BigDecimal.valueOf(100000))
                .discountAmount(BigDecimal.ZERO)
                .shippingFee(BigDecimal.ZERO)
                .totalAmount(BigDecimal.valueOf(100000))
                .currency("VND")
                .build();
    }

    public OrderItem newOrderItem(Order order, int qty, int unitPrice) {
        return OrderItem.builder()
                .order(order)
                .sku("ITEM-" + uniqueSuffix())
                .name("Item " + uniqueSuffix())
                .quantity(qty)
                .unitPrice(BigDecimal.valueOf(unitPrice))
                .totalPrice(BigDecimal.valueOf(qty * (long) unitPrice))
                .build();
    }
}
