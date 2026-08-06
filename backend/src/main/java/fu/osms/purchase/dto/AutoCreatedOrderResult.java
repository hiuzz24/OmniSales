package fu.osms.purchase.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * Result returned when an order is automatically created after receipt completion
 * due to shortage or surplus in the original purchase order.
 */
@Data
@Builder
public class AutoCreatedOrderResult {
    /** "SHORTAGE" or "SURPLUS" */
    private String type;
    private UUID orderId;
    private String orderCode;
    /** Human-readable summary listing affected products and quantities. */
    private String summary;
}
