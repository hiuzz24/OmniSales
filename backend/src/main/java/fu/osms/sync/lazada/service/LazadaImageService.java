package fu.osms.sync.lazada.service;

import fu.osms.catalog.entity.ProductImage;

import java.util.List;
import java.util.UUID;

public interface LazadaImageService {
    String migrateImageUrl(String imageUrl, UUID channelId);

    /**
     * Migrate images from external URLs to Lazada CDN URLs.
     * Throws an exception if any image fails to migrate.
     *
     * @param images         List of product images
     * @param accessToken    Lazada access token
     * @param tokenExpiresAt Expiration time of the token
     * @return List of Lazada CDN image URLs corresponding to the input images
     */
    List<String> migrateImages(List<ProductImage> images, UUID channelId);
}
