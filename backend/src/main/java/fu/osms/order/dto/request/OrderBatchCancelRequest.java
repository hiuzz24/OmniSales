package fu.osms.order.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record OrderBatchCancelRequest(@NotEmpty List<UUID> orderIds) {
}
