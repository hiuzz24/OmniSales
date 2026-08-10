package fu.osms.orderreturn.event;

import java.util.UUID;

/** Fired after payment and inventory projections for a return have finished. */
public record OrderReturnProcessedEvent(UUID returnId) {
}
