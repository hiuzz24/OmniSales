package fu.osms.channel.service.impl;

import fu.osms.channel.dto.response.ChannelConnectionLogResponse;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelConnectionLog;
import fu.osms.channel.enums.ChannelConnectionAction;
import fu.osms.channel.enums.ChannelConnectionLogStatus;
import fu.osms.channel.mapper.ChannelConnectionLogMapper;
import fu.osms.channel.repository.ChannelConnectionLogRepository;
import fu.osms.channel.service.ChannelConnectionLogService;
import fu.osms.common.dto.PageResponse;
import fu.osms.common.enums.PlatformType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChannelConnectionLogServiceImpl implements ChannelConnectionLogService {

    private final ChannelConnectionLogRepository channelConnectionLogRepository;
    private final ChannelConnectionLogMapper channelConnectionLogMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ChannelConnectionLogResponse> search(
            PlatformType platform,
            ChannelConnectionLogStatus status,
            ChannelConnectionAction action,
            UUID channelId,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Specification<ChannelConnectionLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (platform != null) {
                predicates.add(cb.equal(root.get("platform"), platform));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (action != null) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (channelId != null) {
                predicates.add(cb.equal(root.get("channel").get("id"), channelId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<ChannelConnectionLog> logPage = channelConnectionLogRepository.findAll(spec, pageable);
        List<ChannelConnectionLogResponse> content = logPage.getContent().stream()
                .map(channelConnectionLogMapper::toResponse)
                .toList();

        return PageResponse.<ChannelConnectionLogResponse>builder()
                .content(content)
                .page(logPage.getNumber())
                .size(logPage.getSize())
                .totalElements(logPage.getTotalElements())
                .totalPages(logPage.getTotalPages())
                .first(logPage.isFirst())
                .last(logPage.isLast())
                .build();
    }

    @Override
    @Transactional
    public void logSuccess(Channel channel, ChannelConnectionAction action, String message, Map<String, Object> metadata) {
        ChannelConnectionLog log = ChannelConnectionLog.builder()
                .platform(channel.getPlatform())
                .channel(channel)
                .channelName(channel.getDisplayName())
                .action(action)
                .status(ChannelConnectionLogStatus.SUCCESS)
                .message(message)
                .metadata(metadata)
                .build();
        channelConnectionLogRepository.save(log);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logFailure(PlatformType platform, ChannelConnectionAction action, String message, String errorMessage, Map<String, Object> metadata) {
        ChannelConnectionLog log = ChannelConnectionLog.builder()
                .platform(platform)
                .channelName(metadata != null && metadata.get("channelName") != null ? metadata.get("channelName").toString() : null)
                .action(action)
                .status(ChannelConnectionLogStatus.FAILED)
                .message(message)
                .errorMessage(errorMessage)
                .metadata(metadata)
                .build();
        channelConnectionLogRepository.save(log);
    }
}
