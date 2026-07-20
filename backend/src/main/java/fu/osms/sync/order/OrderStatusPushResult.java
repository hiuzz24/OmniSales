package fu.osms.sync.order;

import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.Map;

@Getter
@Builder
public class OrderStatusPushResult {

    private final OrderStatusPushStatus status;
    private final String message;
    @Builder.Default
    private final Map<String, Object> metadata = Collections.emptyMap();

    public static OrderStatusPushResult success(String message, Map<String, Object> metadata) {
        return OrderStatusPushResult.builder()
                .status(OrderStatusPushStatus.SUCCESS)
                .message(message)
                .metadata(metadata != null ? metadata : Collections.emptyMap())
                .build();
    }

    public static OrderStatusPushResult skipped(String message) {
        return OrderStatusPushResult.builder()
                .status(OrderStatusPushStatus.SKIPPED)
                .message(message)
                .build();
    }

    public static OrderStatusPushResult failed(String message) {
        return OrderStatusPushResult.builder()
                .status(OrderStatusPushStatus.FAILED)
                .message(message)
                .build();
    }

    public boolean isSuccess() {
        return status == OrderStatusPushStatus.SUCCESS;
    }

    public boolean isSkipped() {
        return status == OrderStatusPushStatus.SKIPPED;
    }
}
