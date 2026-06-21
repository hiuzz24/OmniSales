package fu.osms.inventory.mapper;

import fu.osms.inventory.dto.response.InventoryTransactionDTO;
import fu.osms.inventory.entity.InventoryTransaction;
import org.mapstruct.*;
import java.math.BigDecimal;

@Mapper(componentModel = "spring")
public interface InventoryTransactionDTOMapper {

    @Mapping(target = "typeLabel", source = "type", qualifiedByName = "mapTypeLabel")
    @Mapping(target = "performedById", source = "performedBy.id")
    @Mapping(target = "performedByName", source = "performedBy.fullName") // Giả định thực tế Entity User có trường fullName

    @Mapping(target = "transactionValue", expression = "java(calculateTransactionValue(transaction))")
    InventoryTransactionDTO toDto(InventoryTransaction transaction);

    @Named("mapTypeLabel")
    default String mapTypeLabel(fu.osms.inventory.enums.InvTxnType type) {
        if (type == null) return "";
        return switch (type) {
            case IMPORT -> "Nhập kho";
            case EXPORT -> "Xuất kho";
            case ADJUSTMENT -> "Điều chỉnh kho";
            case ORDER_DEDUCT -> "Trừ kho (Giữ chỗ đơn hàng)";
            case ORDER_CANCEL -> "Hoàn kho (Hủy đơn hàng)";
            default -> type.name();
        };
    }

    default BigDecimal calculateTransactionValue(InventoryTransaction transaction) {
        if (transaction == null || transaction.getUnitCost() == null || transaction.getQuantityChange() == null) {
            return BigDecimal.ZERO;
        }
        long absoluteChange = Math.abs(transaction.getQuantityChange());
        return transaction.getUnitCost().multiply(BigDecimal.valueOf(absoluteChange));
    }
}