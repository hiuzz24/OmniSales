package fu.osms.sync.tiktok.inventory;

import java.util.List;

public record TikTokInventoryTarget(
        String productId,
        String skuId,
        String primaryWarehouseId,
        List<String> warehouseIds
) {
}
