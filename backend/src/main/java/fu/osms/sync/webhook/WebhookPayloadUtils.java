package fu.osms.sync.webhook;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public final class WebhookPayloadUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private WebhookPayloadUtils() {
    }

    public static Object firstPresent(Map<String, Object> payload, String... keys) {
        if (payload == null) {
            return null;
        }
        for (String key : keys) {
            Object value = payload.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    public static Object firstPresentInData(Map<String, Object> payload, String... keys) {
        Object data = payload != null ? payload.get("data") : null;
        if (!(data instanceof Map<?, ?> dataMap)) {
            return null;
        }
        for (String key : keys) {
            Object value = dataMap.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    public static String text(Object value) {
        return value != null ? value.toString() : null;
    }

    public static BigDecimal decimal(Object value) {
        if (value == null || value.toString().isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    public static Integer integer(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null && !value.toString().isBlank()) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    public static Map<String, Object> copyMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new HashMap<>();
        }
        Map<String, Object> result = new HashMap<>();
        map.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
        return result;
    }

    public static Map<String, Object> parseObject(String response, String errorMessage) {
        try {
            return OBJECT_MAPPER.readValue(response, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException(errorMessage, e);
        }
    }
}
