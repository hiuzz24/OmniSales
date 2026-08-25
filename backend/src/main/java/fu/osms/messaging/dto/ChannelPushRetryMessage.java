package fu.osms.messaging.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Yêu cầu retry đẩy tồn kho/giá lên một kênh sàn sau khi lần đẩy thủ công thất bại.
 * Consumer phải KHÔNG phát hành lại message này khi vẫn lỗi — retry lặp do Spring AMQP
 * đảm nhiệm, hết lượt thì dead-letter sang DLQ.
 */
public record ChannelPushRetryMessage(UUID channelId, String reason, OffsetDateTime requestedAt) {
}
