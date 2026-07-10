package fu.osms.channel.repository;

import fu.osms.channel.entity.Channel;
import fu.osms.common.enums.PlatformType;
import fu.osms.it.IntegrationTestBase;
import fu.osms.it.IntegrationTestCleanupHelper;
import fu.osms.it.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelRepositoryIT extends IntegrationTestBase {

    @Autowired ChannelRepository channelRepo;
    @Autowired TestDataFactory factory;
    @Autowired IntegrationTestCleanupHelper cleanup;

    @BeforeEach
    void setUp() {
        cleanup.truncate();
    }

    @Test
    void findByPlatformAndDeletedAtIsNull_filtersByPlatform() {
        Channel ch = factory.newChannel();
        ch.setPlatform(PlatformType.SHOPIFY);
        channelRepo.save(ch);

        List<Channel> shopify = channelRepo.findByPlatformAndDeletedAtIsNull(PlatformType.SHOPIFY);
        assertThat(shopify).extracting(Channel::getId).contains(ch.getId());

        List<Channel> lazada = channelRepo.findByPlatformAndDeletedAtIsNull(PlatformType.LAZADA);
        assertThat(lazada).extracting(Channel::getId).doesNotContain(ch.getId());
    }

    @Test
    void existsByPlatformAndDisplayName_returnsBoolean() {
        Channel ch = factory.newChannel();
        ch.setPlatform(PlatformType.SHOPIFY);
        ch.setDisplayName("IT Shop " + TestDataFactory.uniqueSuffix());
        channelRepo.save(ch);

        assertThat(channelRepo.existsByPlatformAndDisplayName(ch.getPlatform(), ch.getDisplayName())).isTrue();
        assertThat(channelRepo.existsByPlatformAndDisplayName(ch.getPlatform(), "NOT-EXISTING")).isFalse();
    }

    @Test
    void findByPlatformAndDisplayName_returnsChannel() {
        Channel ch = factory.newChannel();
        ch.setPlatform(PlatformType.SHOPIFY);
        ch.setDisplayName("IT Shop " + TestDataFactory.uniqueSuffix());
        channelRepo.save(ch);

        Optional<Channel> got = channelRepo.findByPlatformAndDisplayName(ch.getPlatform(), ch.getDisplayName());
        assertThat(got).isPresent();
        assertThat(got.get().getId()).isEqualTo(ch.getId());
    }

    @Test
    void findByDeletedAtIsNull_excludesDeleted() {
        Channel ch = channelRepo.save(factory.newChannel());

        List<Channel> active = channelRepo.findByDeletedAtIsNull();
        assertThat(active).extracting(Channel::getId).contains(ch.getId());
    }
}
