package fu.osms.shop.service;

import fu.osms.common.dto.PageResponse;
import fu.osms.shop.dto.request.ShopRequest;
import fu.osms.shop.dto.response.ShopResponse;

import java.util.UUID;

public interface ShopService {

    ShopResponse create(ShopRequest request);

    ShopResponse getById(UUID id);

    PageResponse<ShopResponse> getAll(int page, int size);

    ShopResponse update(UUID id, ShopRequest request);

    void delete(UUID id);
}
