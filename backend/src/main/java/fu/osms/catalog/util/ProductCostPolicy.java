package fu.osms.catalog.util;

import java.math.BigDecimal;

public final class ProductCostPolicy {

    private ProductCostPolicy() {
    }

    public static BigDecimal initialCost(BigDecimal currentCost, BigDecimal salePrice) {
        if (isPositive(currentCost)) {
            return currentCost;
        }
        if (salePrice != null && salePrice.compareTo(BigDecimal.ZERO) >= 0) {
            return salePrice;
        }
        return BigDecimal.ZERO;
    }

    public static BigDecimal weightedAverageCost(BigDecimal currentQuantity,
                                                 BigDecimal currentCost,
                                                 BigDecimal incomingQuantity,
                                                 BigDecimal incomingUnitCost) {
        BigDecimal qtyOnHand = currentQuantity == null ? BigDecimal.ZERO : currentQuantity;
        BigDecimal qtyIncoming = incomingQuantity == null ? BigDecimal.ZERO : incomingQuantity;
        BigDecimal totalQty = qtyOnHand.add(qtyIncoming);

        if (totalQty.compareTo(BigDecimal.ZERO) <= 0) {
            return incomingUnitCost != null ? incomingUnitCost : BigDecimal.ZERO;
        }

        BigDecimal existingValue = qtyOnHand.multiply(
                isPositive(currentCost) ? currentCost : (incomingUnitCost != null ? incomingUnitCost : BigDecimal.ZERO)
        );
        BigDecimal incomingValue = qtyIncoming.multiply(incomingUnitCost != null ? incomingUnitCost : BigDecimal.ZERO);

        return existingValue.add(incomingValue)
                .divide(totalQty, 2, java.math.RoundingMode.HALF_UP);
    }

    public static boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }
}
