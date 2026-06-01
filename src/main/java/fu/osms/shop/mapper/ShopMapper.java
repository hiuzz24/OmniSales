package fu.osms.shop.mapper;

import fu.osms.shop.dto.request.ShopRequest;
import fu.osms.shop.dto.response.ShopResponse;
import fu.osms.shop.entity.Shop;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface ShopMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Shop toEntity(ShopRequest request);

    ShopResponse toResponse(Shop shop);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromRequest(ShopRequest request, @MappingTarget Shop shop);
}
