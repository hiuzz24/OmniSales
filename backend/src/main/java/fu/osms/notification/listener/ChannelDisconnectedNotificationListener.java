package fu.osms.notification.listener;

import fu.osms.channel.entity.Channel;
import fu.osms.channel.event.ChannelDisconnectedEvent;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.notification.service.OrderWorkflowNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelDisconnectedNotificationListener {

    private final ChannelRepository channelRepository;
    private final OrderWorkflowNotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDisconnected(ChannelDisconnectedEvent event) {
        Channel channel = channelRepository.findById(event.channelId()).orElse(null);
        if (channel == null) return;
        try {
            notificationService.notifyRolesOnce(
                    List.of("OWNER", "SYSTEM_ADMIN"),
                    "CHANNEL_DISCONNECTED",
                    "Kênh bán hàng đã ngắt kết nối",
                    "Kênh " + channel.getDisplayName() + " đã bị ngắt kết nối. Hãy kết nối lại để tiếp tục đồng bộ.",
                    "CHANNEL",
                    channel.getId());
        } catch (Exception exception) {
            log.warn("Could not notify disconnected channelId={}: {}",
                    event.channelId(), exception.getMessage());
        }
    }
}
