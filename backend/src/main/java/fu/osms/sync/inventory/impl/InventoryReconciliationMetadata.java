package fu.osms.sync.inventory.impl;

import fu.osms.channel.entity.ChannelProductVariant;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

final class InventoryReconciliationMetadata {

    static final String KEY = "inventoryReconciliation";

    private InventoryReconciliationMetadata() {
    }

    static Map<String, Object> root(ChannelProductVariant mapping) {
        return mapping.getMetadata() == null
                ? new HashMap<>()
                : new HashMap<>(mapping.getMetadata());
    }

    static Map<String, Object> state(ChannelProductVariant mapping) {
        Object value = mapping.getMetadata() == null ? null : mapping.getMetadata().get(KEY);
        if (!(value instanceof Map<?, ?> source)) {
            return new HashMap<>();
        }
        Map<String, Object> copy = new HashMap<>();
        source.forEach((key, item) -> copy.put(String.valueOf(key), item));
        return copy;
    }

    static void write(ChannelProductVariant mapping, Map<String, Object> state) {
        Map<String, Object> root = root(mapping);
        root.put(KEY, new HashMap<>(state));
        mapping.setMetadata(root);
    }

    static void clear(ChannelProductVariant mapping) {
        Map<String, Object> root = root(mapping);
        root.remove(KEY);
        mapping.setMetadata(root);
    }

    static String text(Map<String, Object> state, String key) {
        Object value = state.get(key);
        return value == null ? null : value.toString();
    }

    static int integer(Map<String, Object> state, String key) {
        Object value = state.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? 0 : Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    static OffsetDateTime time(Map<String, Object> state, String key) {
        String value = text(state, key);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
