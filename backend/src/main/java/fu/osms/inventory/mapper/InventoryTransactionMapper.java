package fu.osms.inventory.mapper;

import fu.osms.inventory.dto.response.InventoryTransactionResponse;
import fu.osms.inventory.entity.InventoryTransaction;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface InventoryTransactionMapper {

    @Mapping(target = "warehouseId", source = "warehouse.id")
    @Mapping(target = "warehouseName", source = "warehouse.name")
    @Mapping(target = "variantId", source = "variant.id")
    @Mapping(target = "variantSku", source = "variant.sku")
    @Mapping(target = "variantName", source = "variant.name")
    @Mapping(target = "performedById", source = "performedBy.id")
    @Mapping(target = "performedByName", source = "performedBy.fullName")
    InventoryTransactionResponse toResponse(InventoryTransaction transaction);
}
