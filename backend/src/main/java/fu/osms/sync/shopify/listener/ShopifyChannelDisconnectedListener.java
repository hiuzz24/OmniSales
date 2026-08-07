package fu.osms.sync.shopify.listener;

import fu.osms.channel.event.ChannelDisconnectedEvent;
import fu.osms.sync.shopify.ShopifyDisconnectCleanupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ShopifyChannelDisconnectedListener {

    private final ShopifyDisconnectCleanupService cleanupService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ChannelDisconnectedEvent event) {
        cleanupService.cleanup(event.channelId());
    }
}
