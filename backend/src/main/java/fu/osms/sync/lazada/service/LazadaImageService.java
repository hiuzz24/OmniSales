package fu.osms.sync.lazada.service;

import fu.osms.catalog.entity.ProductImage;
import fu.osms.sync.lazada.dto.LazadaMigratedImages;

import java.util.List;
import java.util.UUID;

public interface LazadaImageService {
    String migrateImageUrl(String imageUrl, UUID channelId);

    /**
     * Migrate images from external URLs to Lazada CDN URLs.
     * Throws an exception if any image fails to migrate.
     *
     * @param images    product and variant images
     * @param channelId Lazada channel ID
     * @return migrated URLs separated into product and variant images
     */
    LazadaMigratedImages migrateImages(List<ProductImage> images, UUID channelId);
}
