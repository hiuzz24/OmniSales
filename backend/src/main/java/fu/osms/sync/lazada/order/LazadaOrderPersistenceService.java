package fu.osms.sync.lazada.order;

import fu.osms.channel.entity.Channel;
import fu.osms.order.entity.Order;
import fu.osms.sync.order.importing.OrderImportOutcome;

public interface LazadaOrderPersistenceService {
    OrderImportOutcome write(Channel channel, LazadaOrderWriteModel model);
    Order getOrder(OrderImportOutcome outcome);
}
