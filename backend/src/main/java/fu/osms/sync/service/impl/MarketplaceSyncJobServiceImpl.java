package fu.osms.sync.service.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.common.enums.SyncStatus;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.sync.dto.MarketplaceSyncJobResponse;
import fu.osms.sync.entity.SyncLog;
import fu.osms.sync.repository.SyncLogRepository;
import fu.osms.sync.service.MarketplaceSyncJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.task.TaskRejectedException;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MarketplaceSyncJobServiceImpl implements MarketplaceSyncJobService {

    private final ChannelRepository channelRepository;
    private final SyncLogRepository syncLogRepository;
    private final MarketplaceSyncJobWorker worker;

    @Override
    public MarketplaceSyncJobResponse enqueueRemoteSync(UUID channelId) {
        Channel channel = channelRepository.findById(channelId)
                .filter(item -> item.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.CHANNEL_NOT_FOUND));
        if (!Boolean.TRUE.equals(channel.getSyncEnabled())) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Kênh đang tắt đồng bộ.");
        }

        var existing = syncLogRepository
                .findFirstByChannelIdAndStatusAndCompletedAtIsNullAndJobTypeEndingWithOrderByStartedAtDesc(
                        channelId,
                        SyncStatus.PENDING,
                        "REMOTE_IMPORT_JOB"
                );
        if (existing.isPresent()) {
            return toResponse(existing.get(), "RUNNING", "Kênh này đang được đồng bộ.");
        }

        SyncLog job = syncLogRepository.save(SyncLog.builder()
                .channel(channel)
                .jobType(channel.getPlatform().name() + "_REMOTE_IMPORT_JOB")
                .status(SyncStatus.PENDING)
                .totalItems(estimatedVariantCount(channel.getMetadata()))
                .startedAt(OffsetDateTime.now())
                .build());
        try {
            worker.executeRemoteSync(job.getId(), channelId);
        } catch (TaskRejectedException exception) {
            job.setStatus(SyncStatus.FAILED);
            job.setFailCount(1);
            job.setErrorSummary("Hàng đợi đồng bộ đang đầy. Vui lòng thử lại sau.");
            job.setCompletedAt(OffsetDateTime.now());
            syncLogRepository.save(job);
            throw new AppException(ErrorCode.INVALID_REQUEST, job.getErrorSummary());
        }
        return toResponse(job, "QUEUED", "Đã bắt đầu đồng bộ từ sàn.");
    }

    @Override
    @Transactional(readOnly = true)
    public MarketplaceSyncJobResponse getJob(UUID jobId) {
        SyncLog job = syncLogRepository.findById(jobId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy tác vụ đồng bộ."));
        String status = job.getStatus() == SyncStatus.PENDING ? "RUNNING" : job.getStatus().name();
        String message = switch (job.getStatus()) {
            case PENDING -> "Đang lấy và xử lý dữ liệu theo từng batch.";
            case SYNCED -> "Đồng bộ từ sàn đã hoàn tất.";
            case FAILED -> job.getErrorSummary();
            default -> "Tác vụ đồng bộ cần được kiểm tra.";
        };
        return toResponse(job, status, message);
    }

    private MarketplaceSyncJobResponse toResponse(SyncLog job, String status, String message) {
        int total = safe(job.getTotalItems());
        int success = safe(job.getSuccessCount());
        int failed = safe(job.getFailCount());
        int processed = success + failed;
        int percent = total <= 0 ? 0 : Math.min(100, (int) Math.round(processed * 100.0 / total));
        Channel channel = job.getChannel();
        return MarketplaceSyncJobResponse.builder()
                .jobId(job.getId())
                .channelId(channel == null ? null : channel.getId())
                .channelName(channel == null ? null : channel.getDisplayName())
                .platform(channel == null ? null : channel.getPlatform())
                .status(status)
                .totalItems(total)
                .processedItems(processed)
                .successCount(success)
                .failCount(failed)
                .progressPercent(percent)
                .message(message)
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .build();
    }

    private int estimatedVariantCount(Map<String, Object> metadata) {
        if (metadata == null) return 0;
        Object value = metadata.get("skuVariantCount");
        if (value instanceof Number number) return Math.max(number.intValue(), 0);
        try {
            return value == null ? 0 : Math.max(Integer.parseInt(value.toString()), 0);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private int safe(Integer value) {
        return value == null ? 0 : value;
    }
}
