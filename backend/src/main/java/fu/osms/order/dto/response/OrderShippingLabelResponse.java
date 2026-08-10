package fu.osms.order.dto.response;

import fu.osms.common.enums.PlatformType;

public record OrderShippingLabelResponse(
        PlatformType platform,
        String documentUrl,
        String format,
        int packageCount
) {
}
