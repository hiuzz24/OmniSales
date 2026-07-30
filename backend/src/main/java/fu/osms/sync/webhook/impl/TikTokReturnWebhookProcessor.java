package fu.osms.sync.webhook.impl;

import fu.osms.common.enums.PlatformType;
import fu.osms.orderreturn.model.OrderReturnSnapshot;
import fu.osms.orderreturn.service.OrderReturnPersistenceService;
import fu.osms.sync.entity.WebhookEvent;
import fu.osms.sync.service.PlatformReturnWebhookProcessor;
import fu.osms.sync.tiktok.returning.TikTokReturnApiService;
import fu.osms.sync.tiktok.returning.TikTokReturnSnapshotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class TikTokReturnWebhookProcessor implements PlatformReturnWebhookProcessor {

    private static final Set<String> RETURN_EVENTS = Set.of(
            "TIKTOK_REVERSE_STATUS_UPDATE", "RETURN_STATUS_CHANGE", "RETURN_STATUS_CHANGED");

    private final TikTokReturnSnapshotMapper mapper;
    private final TikTokReturnApiService returnApiService;
    private final OrderReturnPersistenceService persistenceService;

    @Override
    public boolean supports(WebhookEvent event) {
        return event.getPlatform() == PlatformType.TIKTOK
                && RETURN_EVENTS.contains(normalize(event.getEventType()))
                && mapper.isPhysicalReturn(event.getRawPayload());
    }

    @Override
    public String process(WebhookEvent event) {
        String externalReturnId = mapper.externalReturnId(event.getRawPayload());
        Map<String, Object> detail = returnApiService.getReturn(event.getChannel().getId(), externalReturnId);
        OrderReturnSnapshot snapshot = mapper.map(detail, event.getExternalEventId());
        persistenceService.upsert(event.getChannel(), snapshot);
        return "PROCESSED";
    }

    private String normalize(String value) {
        return value == null ? "" : value.toUpperCase().replace('/', '_');
    }
}
