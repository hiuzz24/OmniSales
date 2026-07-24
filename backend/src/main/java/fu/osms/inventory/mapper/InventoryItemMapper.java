package fu.osms.inventory.mapper;

import fu.osms.inventory.dto.request.InventoryItemRequest;
import fu.osms.inventory.dto.response.InventoryItemResponse;
import fu.osms.inventory.entity.InventoryItem;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface InventoryItemMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "warehouse", ignore = true)
    @Mapping(target = "variant", ignore = true)
    @Mapping(target = "availableQuantity", ignore = true)


    @Mapping(target = "version", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "averageCost", ignore = true)
    InventoryItem toEntity(InventoryItemRequest request);

    @Mapping(target = "warehouseId", source = "warehouse.id")
    @Mapping(target = "warehouseName", source = "warehouse.name")
    @Mapping(target = "productId", source = "variant.product.id")
    @Mapping(target = "variantId", source = "variant.id")
    @Mapping(target = "variantSku", source = "variant.sku")
    @Mapping(target = "productName", source = "variant.product.name")
    @Mapping(target = "variantName", source = "variant.name")
    @Mapping(target = "unitPrice", source = "variant.price")
    @Mapping(target = "salePrice", source = "variant.price")
    @Mapping(target = "currentSalePrice", source = "variant.price")
    @Mapping(target = "categoryId", source = "variant.product.category.id")
    @Mapping(target = "categoryName", source = "variant.product.category.name")
    @Mapping(target = "price", source = "variant.price")
    @Mapping(target = "channelId", ignore = true)
    @Mapping(target = "channelName", ignore = true)
    @Mapping(target = "platform", ignore = true)
    @Mapping(target = "isLowStock",
            expression = "java(item.getAvailableQuantity() != null && item.getAvailableQuantity() <= item.getLowStockThreshold())")
    InventoryItemResponse toResponse(InventoryItem item);

    List<InventoryItemResponse> toResponseList(List<InventoryItem> items);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "warehouse", ignore = true)
    @Mapping(target = "variant", ignore = true)
    @Mapping(target = "availableQuantity", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "averageCost", ignore = true)
    void updateEntityFromRequest(InventoryItemRequest request, @MappingTarget InventoryItem item);
}
