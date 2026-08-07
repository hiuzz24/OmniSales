package fu.osms.sync.order.pull.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.channel.service.ChannelConnectionValidator;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.enums.SyncStatus;
import fu.osms.common.utils.SecurityUtils;
import fu.osms.sync.dto.SyncLogResponse;
import fu.osms.sync.entity.SyncLog;
import fu.osms.sync.mapper.SyncLogMapper;
import fu.osms.sync.order.pull.OrderPullRequestedEvent;
import fu.osms.sync.order.pull.OrderPullService;
import fu.osms.sync.order.pull.dto.OrderPullRequest;
import fu.osms.sync.repository.SyncLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OrderPullServiceImpl implements OrderPullService {
    private static final String JOB_TYPE = "ORDER_PULL";
    private static final Set<PlatformType> SUPPORTED = Set.of(PlatformType.LAZADA, PlatformType.SHOPIFY, PlatformType.TIKTOK);
    private final ChannelRepository channelRepository;
    private final ChannelConnectionValidator connectionValidator;
    private final SyncLogRepository syncLogRepository;
    private final SyncLogMapper syncLogMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public List<SyncLogResponse> start(OrderPullRequest request) {
        OffsetDateTime to = request.to() == null ? OffsetDateTime.now() : request.to();
        OffsetDateTime from = request.from() == null ? to.minusHours(24) : request.from();
        validateRange(from, to);
        List<SyncLogResponse> result = new ArrayList<>();
        request.channelIds().stream().distinct().sorted().forEach(channelId -> {
            Channel channel = channelRepository.findForUpdateById(channelId)
                    .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + channelId));
            connectionValidator.validateConnected(channel);
            validatePlatform(channel);
            expireStale(channelId);
            List<SyncLog> active = syncLogRepository.findByJobTypeAndChannelIdAndStatus(JOB_TYPE, channelId, SyncStatus.PENDING);
            if (!active.isEmpty()) {
                result.add(syncLogMapper.toResponse(active.get(0)));
                return;
            }
            SyncLog log = syncLogRepository.save(SyncLog.builder()
                    .channel(channel).jobType(JOB_TYPE)
                    .idempotencyKey(JOB_TYPE + ":" + channelId + ":" + UUID.randomUUID())
                    .status(SyncStatus.PENDING).successCount(0).failCount(0)
                    .triggeredBy(SecurityUtils.getCurrentUser().orElse(null)).startedAt(OffsetDateTime.now()).build());
            eventPublisher.publishEvent(new OrderPullRequestedEvent(log.getId(), from, to));
            result.add(syncLogMapper.toResponse(log));
        });
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public SyncLogResponse get(UUID id) {
        SyncLog log = syncLogRepository.findWithChannelById(id)
                .orElseThrow(() -> new IllegalArgumentException("Order pull job not found: " + id));
        if (!JOB_TYPE.equals(log.getJobType())) throw new IllegalArgumentException("Sync log is not an order pull job");
        return syncLogMapper.toResponse(log);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SyncLogResponse> active() {
        return syncLogRepository.findActiveJobs(JOB_TYPE, SyncStatus.PENDING).stream().map(syncLogMapper::toResponse).toList();
    }

    private void validateRange(OffsetDateTime from, OffsetDateTime to) {
        if (from.isAfter(to)) throw new IllegalArgumentException("Order pull start time must be before end time");
        if (Duration.between(from, to).compareTo(Duration.ofDays(7)) > 0)
            throw new IllegalArgumentException("Order pull range cannot exceed 7 days");
    }
    private void validatePlatform(Channel channel) {
        if (!SUPPORTED.contains(channel.getPlatform()))
            throw new IllegalArgumentException("Order pull is not supported for " + channel.getPlatform());
        if (channel.getPlatform() == PlatformType.SHOPIFY && missing(channel, "shopDomain"))
            throw new IllegalStateException("Shopify channel is missing shop domain");
        if (channel.getPlatform() == PlatformType.TIKTOK && missing(channel, "shopCipher"))
            throw new IllegalStateException("TikTok channel is missing shopCipher");
    }
    private boolean missing(Channel channel, String key) {
        Object value = channel.getMetadata() == null ? null : channel.getMetadata().get(key);
        return value == null || String.valueOf(value).isBlank();
    }
    private void expireStale(UUID channelId) {
        for (SyncLog stale : syncLogRepository.findStalePending(JOB_TYPE, channelId, OffsetDateTime.now().minusMinutes(30))) {
            stale.setStatus(SyncStatus.FAILED);
            stale.setErrorSummary("[PAGE] Order pull job timed out before completion");
            stale.setCompletedAt(OffsetDateTime.now());
            syncLogRepository.save(stale);
        }
    }
}
