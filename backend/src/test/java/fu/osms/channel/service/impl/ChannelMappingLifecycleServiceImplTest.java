package fu.osms.channel.service.impl;

import fu.osms.channel.entity.ChannelProduct;
import fu.osms.channel.repository.ChannelProductRepository;
import fu.osms.channel.service.model.MappingRestoreResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChannelMappingLifecycleServiceImpl Tests")
class ChannelMappingLifecycleServiceImplTest {

    @Mock private ChannelProductRepository channelProductRepository;

    private ChannelMappingLifecycleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ChannelMappingLifecycleServiceImpl(channelProductRepository);
    }

    private ChannelProduct activeMapping(UUID id) {
        return ChannelProduct.builder().id(id).mappingState("ACTIVE").metadata(new HashMap<>()).build();
    }

    @Test
    @DisplayName("archiveForDisconnect flips all ACTIVE mappings to ARCHIVED and stamps the metadata flag")
    void archiveForDisconnect_flipsAllActiveMappings() {
        UUID channelId = UUID.randomUUID();
        ChannelProduct a = activeMapping(UUID.randomUUID());
        ChannelProduct b = activeMapping(UUID.randomUUID());
        when(channelProductRepository.findByChannelIdAndMappingState(channelId, "ACTIVE"))
                .thenReturn(List.of(a, b));

        int count = service.archiveForDisconnect(channelId);

        assertThat(count).isEqualTo(2);
        assertThat(a.getMappingState()).isEqualTo("ARCHIVED");
        assertThat(b.getMappingState()).isEqualTo("ARCHIVED");
        assertThat(a.getMetadata()).containsEntry("archivedByDisconnect", true);
        assertThat(b.getMetadata()).containsEntry("archivedByDisconnect", true);
        verify(channelProductRepository).saveAll(List.of(a, b));
    }

    @Test
    @DisplayName("archiveForDisconnect returns 0 when no ACTIVE mappings exist for the channel")
    void archiveForDisconnect_noActiveMappings() {
        UUID channelId = UUID.randomUUID();
        when(channelProductRepository.findByChannelIdAndMappingState(channelId, "ACTIVE"))
                .thenReturn(List.of());

        int count = service.archiveForDisconnect(channelId);

        assertThat(count).isZero();
        verify(channelProductRepository).saveAll(List.of());
    }

    @Test
    @DisplayName("restoreAfterReconnect returns MappingRestoreResult.none() when no archived mappings exist")
    void restoreAfterReconnect_noneWhenEmpty() {
        UUID channelId = UUID.randomUUID();
        when(channelProductRepository.findByChannelIdAndMappingState(channelId, "ARCHIVED"))
                .thenReturn(List.of());

        MappingRestoreResult result = service.restoreAfterReconnect(channelId);

        assertThat(result.restoredCount()).isZero();
        assertThat(result.legacyRestore()).isFalse();
    }

    @Test
    @DisplayName("restoreAfterReconnect flips ARCHIVED→ACTIVE and clears the disconnect stamp on stamped rows")
    void restoreAfterReconnect_restoresStamped() {
        UUID channelId = UUID.randomUUID();
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        ChannelProduct a = ChannelProduct.builder().id(idA).mappingState("ARCHIVED").build();
        a.getMetadata().put("archivedByDisconnect", true);
        ChannelProduct b = ChannelProduct.builder().id(idB).mappingState("ARCHIVED").build();
        b.getMetadata().put("archivedByDisconnect", true);
        when(channelProductRepository.findByChannelIdAndMappingState(channelId, "ARCHIVED"))
                .thenReturn(List.of(a, b));

        MappingRestoreResult result = service.restoreAfterReconnect(channelId);

        assertThat(result.restoredCount()).isEqualTo(2);
        assertThat(result.legacyRestore()).isFalse();
        assertThat(a.getMappingState()).isEqualTo("ACTIVE");
        assertThat(b.getMappingState()).isEqualTo("ACTIVE");
        assertThat(a.getMetadata()).doesNotContainKey("archivedByDisconnect");
        assertThat(b.getMetadata()).doesNotContainKey("archivedByDisconnect");
    }

    @Test
    @DisplayName("restoreAfterReconnect falls back to legacy mode when no rows are stamped (back-compat)")
    void restoreAfterReconnect_legacyMode() {
        UUID channelId = UUID.randomUUID();
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        // Archived mappings with NO archivedByDisconnect flag (legacy).
        ChannelProduct a = ChannelProduct.builder().id(idA).mappingState("ARCHIVED").metadata(new HashMap<>()).build();
        ChannelProduct b = ChannelProduct.builder().id(idB).mappingState("ARCHIVED").metadata(new HashMap<>()).build();
        when(channelProductRepository.findByChannelIdAndMappingState(channelId, "ARCHIVED"))
                .thenReturn(List.of(a, b));

        MappingRestoreResult result = service.restoreAfterReconnect(channelId);

        assertThat(result.restoredCount()).isEqualTo(2);
        assertThat(result.legacyRestore()).isTrue();
        assertThat(a.getMappingState()).isEqualTo("ACTIVE");
        assertThat(b.getMappingState()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("restoreAfterReconnect tolerates a null metadata field on a legacy mapping")
    void restoreAfterReconnect_handlesNullMetadata() {
        UUID channelId = UUID.randomUUID();
        ChannelProduct mapping = ChannelProduct.builder().id(UUID.randomUUID()).mappingState("ARCHIVED").build();
        // metadata is null by Lombok default? ChannelProduct uses @Builder.Default to a HashMap.
        // Test explicitly to catch any regression if @Builder.Default changes.
        List<ChannelProduct> list = new ArrayList<>();
        list.add(mapping);
        when(channelProductRepository.findByChannelIdAndMappingState(channelId, "ARCHIVED"))
                .thenReturn(list);

        MappingRestoreResult result = service.restoreAfterReconnect(channelId);

        assertThat(mapping.getMappingState()).isEqualTo("ACTIVE");
        assertThat(result.legacyRestore()).isTrue();
        verify(channelProductRepository).saveAll(any());
    }
}
