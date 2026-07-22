package fu.osms.sync.shopify.order;

import fu.osms.channel.entity.Channel;
import fu.osms.order.entity.Order;
import fu.osms.sync.order.importing.OrderImportOutcome;

public interface ShopifyOrderPersistenceService {
    OrderImportOutcome write(Channel channel, ShopifyOrderWriteModel model);
    Order getOrder(OrderImportOutcome outcome);
}
