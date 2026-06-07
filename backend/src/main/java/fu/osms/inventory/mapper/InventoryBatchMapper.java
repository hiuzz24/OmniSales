package fu.osms.inventory.mapper;

import fu.osms.inventory.dto.response.InventoryBatchResponse;
import fu.osms.inventory.entity.InventoryBatch;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface InventoryBatchMapper {

    @Mapping(target = "shopId", source = "shop.id")
    @Mapping(target = "warehouseId", source = "warehouse.id")
    @Mapping(target = "warehouseName", source = "warehouse.name")
    @Mapping(target = "variantId", source = "variant.id")
    @Mapping(target = "variantSku", source = "variant.sku")
    @Mapping(target = "variantName", source = "variant.name")
    InventoryBatchResponse toResponse(InventoryBatch batch);
}
