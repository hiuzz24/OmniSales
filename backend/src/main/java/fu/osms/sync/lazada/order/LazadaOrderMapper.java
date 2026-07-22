package fu.osms.sync.lazada.order;

import java.util.List;
import java.util.Map;

public interface LazadaOrderMapper {
    LazadaOrderWriteModel map(LazadaOrderStatusContext context, Map<String, Object> orderData,
                              List<Map<String, Object>> items);
}
