package fu.osms.messaging.listener;

import fu.osms.messaging.constants.RabbitMQConstants;
import fu.osms.messaging.dto.ChannelPushRetryMessage;
import fu.osms.sync.service.ChannelLocalSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Retry nền cho luồng đẩy tồn kho/giá lên sàn khi lần đẩy thủ công thất bại.
 * Mốc {@code lastSyncedApplicationAt} của kênh không được advance khi lỗi
 * (transaction rollback), nên mọi SKU chưa đồng bộ vẫn nằm chờ và có thể
 * được đẩy lại thủ công bất cứ lúc nào.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelPushRetryEventListener {

    private final ChannelLocalSyncService channelLocalSyncService;

    @RabbitListener(queues = RabbitMQConstants.QUEUE_CHANNEL_PUSH_RETRY, concurrency = "1-2")
    public void onChannelPushRetry(ChannelPushRetryMessage message) {
        log.info("[ChannelPushRetryEventListener] Retrying channel push channelId={} requestedAt={} reason={}",
                message.channelId(), message.requestedAt(), message.reason());
        channelLocalSyncService.retryLocalSync(message.channelId());
    }
}
