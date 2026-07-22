package fu.osms.sync.order.pull.dto;

import jakarta.validation.constraints.NotEmpty;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OrderPullRequest(@NotEmpty List<UUID> channelIds, OffsetDateTime from, OffsetDateTime to) {
}
