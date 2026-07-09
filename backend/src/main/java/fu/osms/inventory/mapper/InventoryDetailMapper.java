package fu.osms.inventory.mapper;

import fu.osms.inventory.dto.response.InventoryDetailDTO;
import fu.osms.inventory.entity.InventoryItem;
import org.mapstruct.*;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Mapper(componentModel = "spring")
public interface InventoryDetailMapper {
    @Mapping(target = "warehouseId", source = "warehouse.id")
    @Mapping(target = "variantId", source = "variant.id")
    @Mapping(target = "channelId", ignore = true)
    @Mapping(target = "platform", ignore = true)
    @Mapping(target = "quantityOnHand", source = "quantityOnHand")
    @Mapping(target = "averageCost", source = "averageCost")
    @Mapping(target = "variantSku", source = "variant.sku")
    @Mapping(target = "productVariantName", source = "variant.name")
    @Mapping(target = "categoryName", source = "variant.product.category.name")
    @Mapping(target = "price", source = "variant.price")
    @Mapping(target = "lastImportedAt", ignore = true)
    @Mapping(target = "lastUpdatedAt", ignore = true)
    @Mapping(target = "totalInventoryValue", ignore = true)
    @Mapping(target = "profitMargin", ignore = true)
    @Mapping(target = "stockStatus", ignore = true)
    @Mapping(target = "warehouseName", source = "warehouse.name")
    InventoryDetailDTO toDetailDTO(InventoryItem entity);

    @AfterMapping
    default void calculateFields(InventoryItem entity, @MappingTarget InventoryDetailDTO dto) {
        if (entity == null) return;

        if (entity.getQuantityOnHand() != null && entity.getAverageCost() != null) {
            BigDecimal qty = BigDecimal.valueOf(entity.getQuantityOnHand());
            dto.setTotalInventoryValue(entity.getAverageCost().multiply(qty));
        } else {
            dto.setTotalInventoryValue(BigDecimal.ZERO);
        }

        // 2. Tính % Biên lợi nhuận = ((price - average_cost) / price) * 100
        if (entity.getVariant() != null && entity.getVariant().getPrice() != null
                && entity.getAverageCost() != null && entity.getVariant().getPrice().compareTo(BigDecimal.ZERO) > 0) {

            BigDecimal price = entity.getVariant().getPrice();
            BigDecimal cost = entity.getAverageCost();
            BigDecimal profit = price.subtract(cost);

            BigDecimal margin = profit.divide(price, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            dto.setProfitMargin(margin.doubleValue());
        } else {
            dto.setProfitMargin(0.0);
        }

        int qoh = entity.getQuantityOnHand() != null ? entity.getQuantityOnHand() : 0;
        int reorder = entity.getLowStockThreshold() != null ? entity.getLowStockThreshold() : 0;

        if (qoh <= 0) {
            dto.setStockStatus("Hết hàng");
        } else if (qoh <= reorder) {
            dto.setStockStatus("Sắp hết");
        } else {
            dto.setStockStatus("Còn hàng");
        }
    }
}
