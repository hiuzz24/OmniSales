package fu.osms.catalog.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.channel.entity.ChannelProduct;
import fu.osms.common.enums.SyncStatus;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

final class ChannelProductConfigMetadataCodec {

    private static final String CONFIG_KEY = "platformConfig";

    private final ObjectMapper objectMapper;

    ChannelProductConfigMetadataCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> decode(ChannelProduct channelProduct) {
        Map<String, Object> metadata = channelProduct.getMetadata() == null
                ? new HashMap<>()
                : new HashMap<>(channelProduct.getMetadata());
        Object value = metadata.get(CONFIG_KEY);
        return value instanceof Map<?, ?> map
                ? objectMapper.convertValue(map, Map.class)
                : new HashMap<>();
    }

    void persist(ChannelProduct channelProduct, Map<String, Object> config) {
        Map<String, Object> metadata = channelProduct.getMetadata() == null
                ? new HashMap<>()
                : new HashMap<>(channelProduct.getMetadata());
        metadata.put(CONFIG_KEY, config);
        channelProduct.setMetadata(metadata);
    }

    void persistChange(ChannelProduct channelProduct,
                       Map<String, Object> previousConfig,
                       Map<String, Object> nextConfig) {
        if (!Objects.equals(previousConfig, nextConfig)) {
            channelProduct.setSyncStatus(SyncStatus.PENDING);
            channelProduct.setLastSyncError(null);
        }
        persist(channelProduct, nextConfig);
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map
                ? objectMapper.convertValue(map, Map.class)
                : new HashMap<>();
    }

    Map<String, Map<String, String>> nestedStringMapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new HashMap<>();
        }
        Map<String, Map<String, String>> result = new HashMap<>();
        map.forEach((key, nestedValue) -> {
            if (!(nestedValue instanceof Map<?, ?> nestedMap)) {
                return;
            }
            Map<String, String> entries = new HashMap<>();
            nestedMap.forEach((nestedKey, entryValue) -> {
                if (nestedKey != null && entryValue != null) {
                    entries.put(String.valueOf(nestedKey), String.valueOf(entryValue));
                }
            });
            result.put(String.valueOf(key), entries);
        });
        return result;
    }
}
