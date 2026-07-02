package fu.osms.sync.service.impl;

import fu.osms.common.enums.PlatformType;
import fu.osms.sync.entity.WebhookEvent;
import fu.osms.sync.service.PlatformOrderWebhookProcessor;
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

    @Override
    public String process(WebhookEvent event) {
        log.info("[processor webhook]");
        String eventType = event.getEventType() != null ? event.getEventType().toUpperCase() : "";
        if (!eventType.contains("ORDER")) {
            return "IGNORED";
        }

        PlatformOrderWebhookProcessor processor = processorMap().get(event.getPlatform());
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
