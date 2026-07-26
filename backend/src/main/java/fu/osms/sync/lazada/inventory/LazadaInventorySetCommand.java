package fu.osms.sync.lazada.inventory;

public record LazadaInventorySetCommand(
        LazadaInventoryKey key,
        int targetSellable
) {
}
