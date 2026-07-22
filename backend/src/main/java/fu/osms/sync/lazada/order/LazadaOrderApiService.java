package fu.osms.sync.lazada.order;

import fu.osms.channel.entity.Channel;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public interface LazadaOrderApiService {
    Map<String, Object> getOrder(Channel channel, String orderId);
    List<Map<String, Object>> getOrderItems(Channel channel, String orderId);
    List<String> listOrderIds(Channel channel, OffsetDateTime from, OffsetDateTime to, int offset, int limit);
}
