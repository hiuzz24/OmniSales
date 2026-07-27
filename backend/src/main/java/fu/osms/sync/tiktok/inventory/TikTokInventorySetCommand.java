package fu.osms.sync.tiktok.inventory;

public record TikTokInventorySetCommand(
        TikTokInventoryTarget target,
        int targetAvailable
) {
}
