package fu.osms.catalog.service;

import fu.osms.catalog.dto.request.ProductRequest;
import fu.osms.catalog.dto.response.ProductResponse;
import fu.osms.catalog.dto.response.ProductSyncQueuedResponse;
import fu.osms.catalog.enums.ProductStatus;
import fu.osms.common.dto.PageResponse;
import fu.osms.common.enums.PlatformType;
import fu.osms.sync.dto.SyncResult;

import java.util.Collection;
import java.util.UUID;

public interface ProductService {

    ProductResponse create(ProductRequest request);

    ProductResponse getById(UUID id);

    PageResponse<ProductResponse> search(String keyword, ProductStatus status, Collection<PlatformType> platforms, int page, int size);

    ProductResponse update(UUID id, ProductRequest request);

    ProductResponse updateStatus(UUID id, ProductStatus status);

    void delete(UUID id);

    SyncResult syncProductToAllChannels(UUID productId);

    SyncResult syncProductToChannel(UUID productId, UUID channelId);

    ProductSyncQueuedResponse syncProductToAllChannelsAsync(UUID productId);

    ProductSyncQueuedResponse syncProductToChannelAsync(UUID productId, UUID channelId);
}
