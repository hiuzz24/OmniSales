package fu.osms.inventory.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Result of comparing default warehouse addresses across connected marketplaces.
 */
@Data
@Builder
public class WarehouseAddressComparisonResult {

    /**
     * ALL_SAME: all platform addresses match each other (but may differ from current).
     * ALL_DIFFERENT: platform addresses are not consistent.
     * SAME_AS_CURRENT: all platform addresses match the current warehouse address.
     * NO_CHANNELS: no connected marketplace channels.
     */
    private String status;

    private String currentWarehouseId;

    private String currentWarehouseAddress;

    private List<PlatformAddress> platformAddresses;

    @Data
    @Builder
    public static class PlatformAddress {
        private String platform;
        private String channelName;
        private String channelId;
        private String address;
    }
}
