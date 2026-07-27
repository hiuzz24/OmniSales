package fu.osms.sync.lazada.inventory;

public record LazadaInventoryKey(
        String itemId,
        String skuId,
        String sellerSku,
        String warehouseCode
) {
}
