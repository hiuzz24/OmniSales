package fu.osms.sync.webhook.impl;

import fu.osms.common.enums.PlatformType;
import fu.osms.orderreturn.model.OrderReturnSnapshot;
import fu.osms.orderreturn.service.OrderReturnPersistenceService;
import fu.osms.sync.entity.WebhookEvent;
import fu.osms.sync.lazada.returning.LazadaReturnSnapshotMapper;
import fu.osms.sync.service.PlatformReturnWebhookProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LazadaReturnWebhookProcessor implements PlatformReturnWebhookProcessor {

    private final LazadaReturnSnapshotMapper mapper;
    private final OrderReturnPersistenceService persistenceService;

    @Override
    public boolean supports(WebhookEvent event) {
        return event.getPlatform() == PlatformType.LAZADA
                && "REVERSE_ORDER".equalsIgnoreCase(event.getEventType())
                && mapper.isPhysicalReturn(event.getRawPayload());
    }

    @Override
    public String process(WebhookEvent event) {
        OrderReturnSnapshot snapshot = mapper.map(event.getRawPayload(), event.getExternalEventId());
        persistenceService.upsert(event.getChannel(), snapshot);
        return "PROCESSED";
    }
}
