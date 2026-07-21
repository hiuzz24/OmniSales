package fu.osms.channel.service.impl;

import fu.osms.channel.entity.ChannelProduct;
import fu.osms.channel.repository.ChannelProductRepository;
import fu.osms.channel.service.ChannelMappingLifecycleService;
import fu.osms.channel.service.model.MappingRestoreResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChannelMappingLifecycleServiceImpl implements ChannelMappingLifecycleService {

    private static final String ARCHIVED_BY_DISCONNECT = "archivedByDisconnect";

    private final ChannelProductRepository channelProductRepository;

    @Override
    public int archiveForDisconnect(UUID channelId) {
        List<ChannelProduct> activeMappings = channelProductRepository
                .findByChannelIdAndMappingState(channelId, "ACTIVE");
        activeMappings.forEach(mapping -> {
            Map<String, Object> metadata = mutableMetadata(mapping);
            metadata.put(ARCHIVED_BY_DISCONNECT, true);
            mapping.setMetadata(metadata);
            mapping.setMappingState("ARCHIVED");
        });
        channelProductRepository.saveAll(activeMappings);
        return activeMappings.size();
    }

    @Override
    public MappingRestoreResult restoreAfterReconnect(UUID channelId) {
        List<ChannelProduct> archived = channelProductRepository
                .findByChannelIdAndMappingState(channelId, "ARCHIVED");
        if (archived.isEmpty()) {
            return MappingRestoreResult.none();
        }

        List<ChannelProduct> marked = archived.stream()
                .filter(this::wasArchivedByDisconnect)
                .toList();
        boolean legacyRestore = marked.isEmpty();
        List<ChannelProduct> toRestore = legacyRestore ? archived : marked;

        toRestore.forEach(mapping -> {
            Map<String, Object> metadata = mutableMetadata(mapping);
            metadata.remove(ARCHIVED_BY_DISCONNECT);
            mapping.setMetadata(metadata);
            mapping.setMappingState("ACTIVE");
        });
        channelProductRepository.saveAll(toRestore);
        return new MappingRestoreResult(toRestore.size(), legacyRestore);
    }

    private boolean wasArchivedByDisconnect(ChannelProduct mapping) {
        return mapping.getMetadata() != null
                && Boolean.TRUE.equals(mapping.getMetadata().get(ARCHIVED_BY_DISCONNECT));
    }

    private Map<String, Object> mutableMetadata(ChannelProduct mapping) {
        return mapping.getMetadata() == null
                ? new HashMap<>() : new HashMap<>(mapping.getMetadata());
    }
}
