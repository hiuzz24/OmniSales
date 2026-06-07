package fu.osms.inventory.mapper;

import fu.osms.inventory.dto.request.InventoryItemRequest;
import fu.osms.inventory.dto.response.InventoryItemResponse;
import fu.osms.inventory.entity.InventoryItem;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface InventoryItemMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "shop", ignore = true)
    @Mapping(target = "warehouse", ignore = true)
    @Mapping(target = "variant", ignore = true)
    @Mapping(target = "availableQuantity", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    InventoryItem toEntity(InventoryItemRequest request);

    @Mapping(target = "shopId", source = "shop.id")
    @Mapping(target = "warehouseId", source = "warehouse.id")
    @Mapping(target = "warehouseName", source = "warehouse.name")
    @Mapping(target = "variantId", source = "variant.id")
    @Mapping(target = "variantSku", source = "variant.sku")
    @Mapping(target = "variantName", source = "variant.name")
    @Mapping(target = "productName", source = "variant.product.name")
    @Mapping(target = "isLowStock",
             expression = "java(item.getAvailableQuantity() != null && item.getAvailableQuantity() <= item.getLowStockThreshold())")
    InventoryItemResponse toResponse(InventoryItem item);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "shop", ignore = true)
    @Mapping(target = "warehouse", ignore = true)
    @Mapping(target = "variant", ignore = true)
    @Mapping(target = "availableQuantity", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromRequest(InventoryItemRequest request, @MappingTarget InventoryItem item);
}
