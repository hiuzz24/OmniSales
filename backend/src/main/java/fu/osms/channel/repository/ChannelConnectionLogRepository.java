package fu.osms.channel.repository;

import fu.osms.channel.entity.ChannelConnectionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ChannelConnectionLogRepository extends JpaRepository<ChannelConnectionLog, UUID>, JpaSpecificationExecutor<ChannelConnectionLog> {
}
