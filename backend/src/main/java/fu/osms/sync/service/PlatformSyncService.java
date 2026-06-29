package fu.osms.sync.service;

import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductImage;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelProduct;

import java.util.List;

public interface PlatformSyncService {

    boolean syncProduct(Product product,
                        List<ProductVariant> variants,
                        List<ProductImage> images,
                        Channel channel,
                        ChannelProduct channelProduct);
}
