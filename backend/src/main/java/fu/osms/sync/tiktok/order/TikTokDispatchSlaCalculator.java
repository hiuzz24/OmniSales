package fu.osms.sync.tiktok.order;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;

@Component
public class TikTokDispatchSlaCalculator {
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<Integer> RTS_PRIORITIES = Set.of(100, 200, 300);

    private final TikTokSlaProperties properties;
    private final long fallbackHours;

    public TikTokDispatchSlaCalculator(
            TikTokSlaProperties properties,
            @Value("${osms.order.waiting-stock-timeout-hours:48}") long fallbackHours) {
        this.properties = properties;
        this.fallbackHours = fallbackHours;
    }

    public SlaWindow calculate(TikTokOrderWriteModel model, OffsetDateTime waitingStockAt) {
        OffsetDateTime dispatch = selectPlatformDeadline(model);
        boolean standardTikTok = "TIKTOK".equalsIgnoreCase(model.shippingType())
                && !Boolean.TRUE.equals(model.preOrder());
        if (dispatch == null) {
            dispatch = standardTikTok
                    ? fallbackVietnamDeadline(model.createdAt() != null ? model.createdAt() : waitingStockAt)
                    : waitingStockAt.plusHours(fallbackHours);
        }
        OffsetDateTime effective = earlier(dispatch, model.shippingDueTime());
        return new SlaWindow(dispatch, effective.minusHours(Math.max(0, properties.getBufferHours())));
    }

    private OffsetDateTime selectPlatformDeadline(TikTokOrderWriteModel model) {
        if (model.fulfillmentPriorityLevel() != null
                && RTS_PRIORITIES.contains(model.fulfillmentPriorityLevel())) {
            return model.rtsSlaTime();
        }
        return model.ttsSlaTime();
    }

    private OffsetDateTime fallbackVietnamDeadline(OffsetDateTime createdAt) {
        ZonedDateTime local = createdAt.atZoneSameInstant(VIETNAM);
        LocalDate date = local.toLocalDate();
        boolean beforeCutoff = isBusinessDay(date) && local.toLocalTime().isBefore(LocalTime.of(14, 0));
        LocalDate deadlineDate = beforeCutoff ? date : nextBusinessDay(date);
        return deadlineDate.atTime(23, 59, 59).atZone(VIETNAM).toOffsetDateTime();
    }

    private LocalDate nextBusinessDay(LocalDate date) {
        LocalDate candidate = date.plusDays(1);
        while (!isBusinessDay(candidate)) candidate = candidate.plusDays(1);
        return candidate;
    }

    private boolean isBusinessDay(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SUNDAY
                && properties.getVietnamHolidays().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(LocalDate::parse)
                .noneMatch(date::equals);
    }

    private OffsetDateTime earlier(OffsetDateTime first, OffsetDateTime second) {
        if (second == null) return first;
        return first.isBefore(second) ? first : second;
    }

    public record SlaWindow(OffsetDateTime dispatchSlaAt, OffsetDateTime allocationCutoffAt) {}
}
