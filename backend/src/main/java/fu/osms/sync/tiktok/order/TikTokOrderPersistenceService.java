package fu.osms.sync.tiktok.order;

import fu.osms.order.entity.Order;
import fu.osms.sync.order.importing.OrderImportOutcome;

public interface TikTokOrderPersistenceService {
    OrderImportOutcome write(TikTokOrderWriteContext context, TikTokOrderWriteModel model);
    Order getOrder(OrderImportOutcome outcome);
}
