package fu.osms.orderreturn.model;

public record ReturnRejectCommand(
        String reasonCode,
        String comment
) {
}
