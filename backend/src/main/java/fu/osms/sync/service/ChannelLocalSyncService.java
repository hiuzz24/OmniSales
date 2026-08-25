package fu.osms.sync.service;

import fu.osms.channel.dto.response.ChannelImportSyncResponse;

import java.util.UUID;

public interface ChannelLocalSyncService {

    ChannelImportSyncResponse syncLocalChanges(UUID channelId);

    /**
     * Chạy lại đẩy tồn kho/giá lên một kênh từ hàng đợi retry. Khi vẫn thất bại,
     * lỗi được xử lý bởi Spring AMQP retry/DLQ và KHÔNG phát hành message retry mới.
     */
    ChannelImportSyncResponse retryLocalSync(UUID channelId);

    ChannelImportSyncResponse syncAllLocalChanges(UUID requestedChannelId);

    ChannelImportSyncResponse syncAllLocalChanges();
}
