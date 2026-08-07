package fu.osms.sync.shopify.inventory;

public record InventoryLocationKey(
        String inventoryItemId,
        String locationId
) {
}
