package fu.osms.sync.order.importing;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

public final class PlatformOrderTimestampParser {

    private static final List<DateTimeFormatter> OFFSET_FORMATTERS = List.of(
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")
    );

    private PlatformOrderTimestampParser() {
    }

    public static OffsetDateTime parse(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        String text = value.toString().trim();
        if (text.matches("\\d+")) {
            try {
                long epoch = Long.parseLong(text);
                Instant instant = text.length() > 10
                        ? Instant.ofEpochMilli(epoch)
                        : Instant.ofEpochSecond(epoch);
                return instant.atOffset(ZoneOffset.UTC);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        for (DateTimeFormatter formatter : OFFSET_FORMATTERS) {
            try {
                return OffsetDateTime.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next platform format.
            }
        }
        return null;
    }
}
