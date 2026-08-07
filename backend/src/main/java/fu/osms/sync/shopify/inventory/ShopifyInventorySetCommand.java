package fu.osms.sync.shopify.inventory;

public record ShopifyInventorySetCommand(
        InventoryLocationKey key,
        int targetAvailable,
        Integer changeFromQuantity
) {
}
