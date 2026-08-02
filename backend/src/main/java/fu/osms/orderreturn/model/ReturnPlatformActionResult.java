package fu.osms.orderreturn.model;

public record ReturnPlatformActionResult(
        OrderReturnSnapshot snapshot,
        boolean applied,
        boolean retrySafe,
        String message
) {
    public ReturnPlatformActionResult(OrderReturnSnapshot snapshot, boolean applied) {
        this(snapshot, applied, !applied, null);
    }

    public static ReturnPlatformActionResult indeterminate(
            OrderReturnSnapshot snapshot,
            String message
    ) {
        return new ReturnPlatformActionResult(snapshot, false, false, message);
    }
}
