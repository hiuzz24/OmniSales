package fu.osms.sync.lazada.dto;

public record LazadaInventorySyncResult(
        int affectedProductCount,
        int pushedVariantCount,
        int changedWarehouseCount
) {
}
