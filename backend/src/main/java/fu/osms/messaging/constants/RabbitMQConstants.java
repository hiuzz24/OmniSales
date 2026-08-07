package fu.osms.messaging.constants;

public final class RabbitMQConstants {

    private RabbitMQConstants() {}

    public static final String TOPIC_EXCHANGE = "osms.topic.exchange";

    public static final String DLX_EXCHANGE = "osms.dlx.exchange";

    public static final String DLQ_PREFIX = "osms.dlq.";

    public static final String ORDER_CREATED        = "order.created";
    public static final String ORDER_CANCELLED      = "order.cancelled";
    public static final String ORDER_PAID           = "order.paid";
    public static final String ORDER_SHIPPED        = "order.shipped";
    public static final String ORDER_DELIVERED      = "order.delivered";

    public static final String INVENTORY_UPDATED    = "inventory.updated";
    public static final String INVENTORY_LOW_STOCK  = "inventory.low-stock";

    public static final String SYNC_ORDER           = "sync.order";
    public static final String SYNC_PRODUCT         = "sync.product";
    public static final String PRODUCT_SYNC_PUSH    = "sync.product.push";

    public static final String NOTIFICATION_SEND    = "notification.send";

    public static final String QUEUE_INVENTORY_ORDER_CREATED    = "queue.inventory.order-created";
    public static final String QUEUE_INVENTORY_ORDER_CANCELLED  = "queue.inventory.order-cancelled";

    public static final String QUEUE_NOTIFICATION_ORDER_CREATED = "queue.notification.order-created";
    public static final String QUEUE_NOTIFICATION_ORDER_PAID    = "queue.notification.order-paid";
    public static final String QUEUE_NOTIFICATION_SEND          = "queue.notification.send";

    public static final String QUEUE_SYNC_ORDER_CREATED = "queue.sync.order-created";
    public static final String QUEUE_SYNC_PRODUCT       = "queue.sync.product";

    public static final String QUEUE_WEBHOOK_SYNC_ORDER   = "queue.webhook.sync-order";
    public static final String QUEUE_WEBHOOK_SYNC_PRODUCT = "queue.webhook.sync-product";
    public static final String QUEUE_PRODUCT_PUSH         = "queue.product.push";
    public static final String QUEUE_INVENTORY_PUSH       = "queue.inventory.push";
}
