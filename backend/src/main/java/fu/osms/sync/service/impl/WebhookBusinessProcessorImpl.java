package fu.osms.sync.service.impl;

import fu.osms.common.enums.PlatformType;
import fu.osms.sync.entity.WebhookEvent;
import fu.osms.sync.service.PlatformCatalogWebhookProcessor;
import fu.osms.sync.service.PlatformOrderWebhookProcessor;
import fu.osms.sync.service.PlatformReturnWebhookProcessor;
import fu.osms.sync.service.WebhookBusinessProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookBusinessProcessorImpl implements WebhookBusinessProcessor {

    private final List<PlatformOrderWebhookProcessor> orderWebhookProcessors;
    private final List<PlatformCatalogWebhookProcessor> catalogWebhookProcessors;
    private final List<PlatformReturnWebhookProcessor> returnWebhookProcessors;

    @Override
    public String process(WebhookEvent event) {
        String eventType = event.getEventType() != null ? event.getEventType().toUpperCase() : "";
        PlatformReturnWebhookProcessor returnProcessor = returnWebhookProcessors.stream()
                .filter(candidate -> candidate.supports(event))
                .findFirst()
                .orElse(null);
        if (returnProcessor != null) {
            return returnProcessor.process(event);
        }
        if ("TIKTOK_INVENTORY_CHANGED".equals(eventType)) {
            return processCatalogEvent(event);
        }
        if (event.getPlatform() != PlatformType.TIKTOK && !eventType.contains("ORDER")) {
            return processCatalogEvent(event);
        }

        PlatformOrderWebhookProcessor processor = processorMap().get(event.getPlatform());
        if (processor == null) {
            return "IGNORED";
        }
        return processor.process(event);
    }

    private String processCatalogEvent(WebhookEvent event) {
        PlatformCatalogWebhookProcessor processor = catalogWebhookProcessors.stream()
                .filter(candidate -> candidate.getPlatform() == event.getPlatform())
                .filter(candidate -> candidate.supports(event))
                .findFirst()
                .orElse(null);
        if (processor == null) {
            return "IGNORED";
        }
        return processor.process(event);
    }

    private Map<PlatformType, PlatformOrderWebhookProcessor> processorMap() {
        return orderWebhookProcessors.stream()
                .collect(Collectors.toMap(PlatformOrderWebhookProcessor::getPlatform, Function.identity()));
    }
}
