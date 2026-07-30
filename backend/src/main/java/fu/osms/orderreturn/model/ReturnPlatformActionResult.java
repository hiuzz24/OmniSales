package fu.osms.orderreturn.model;

public record ReturnPlatformActionResult(
        OrderReturnSnapshot snapshot,
        boolean applied
) {
}
