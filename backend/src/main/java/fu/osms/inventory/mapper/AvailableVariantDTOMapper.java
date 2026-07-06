package fu.osms.inventory.mapper;

import fu.osms.catalog.entity.ProductVariant;
import fu.osms.inventory.dto.response.AvailableVariantDTO;
import fu.osms.inventory.entity.InventoryItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AvailableVariantDTOMapper {

    @Mapping(target = "variantId", source = "variant.id")
    @Mapping(target = "sku", source = "variant.sku")
    @Mapping(target = "productName", source = "variant.product.name")
    @Mapping(target = "variantName", source = "variant.name")
    @Mapping(target = "unitPrice", source = "variant.price")
    @Mapping(target = "averageCost", source = "item.averageCost")
    @Mapping(target = "availableQuantity", source = "item.availableQuantity")
    AvailableVariantDTO toAvailableDto(ProductVariant variant, InventoryItem item);

}
