package fu.osms.system.repository;

import java.time.Instant;
import java.util.UUID;

public interface SystemLogProjection {
    UUID getId();
    String getType();
    String getMessage();
    String getUser();
    String getIp();
    String getDetails();
    Instant getTimestamp();
}
