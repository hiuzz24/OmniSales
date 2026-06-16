package fu.osms.inventory.mapper;

import fu.osms.inventory.dto.request.StockReceiveItemRequest;
import fu.osms.inventory.dto.request.StockReceiveRequest;
import fu.osms.inventory.dto.response.StockReceiveItemResponse;
import fu.osms.inventory.dto.response.StockReceiveResponse;
import fu.osms.inventory.entity.InventoryReceipt;
import fu.osms.inventory.entity.InventoryReceiptItem;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface StockReceiveMapper {

    // ── Receipt ───────────────────────────────────────────────────────────────

    @Mapping(target = "id", ignore = true)

    @Mapping(target = "warehouse", ignore = true)
    @Mapping(target = "supplier", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "totalCost", ignore = true)
    @Mapping(target = "receivedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "approvedBy", ignore = true)
    @Mapping(target = "confirmedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    InventoryReceipt toEntity(StockReceiveRequest request);

    @Mapping(target = "warehouseId", source = "warehouse.id")
    @Mapping(target = "warehouseName", source = "warehouse.name")
    @Mapping(target = "supplierId", source = "supplier.id")
    @Mapping(target = "supplierName", source = "supplier.name")
    @Mapping(target = "createdById", source = "createdBy.id")
    @Mapping(target = "createdByName", source = "createdBy.fullName")
    @Mapping(target = "approvedById", source = "approvedBy.id")
    @Mapping(target = "approvedByName", source = "approvedBy.fullName")
    @Mapping(target = "items", ignore = true)
    StockReceiveResponse toResponse(InventoryReceipt receipt);

    // ── Receipt Item ──────────────────────────────────────────────────────────

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "receipt", ignore = true)
    @Mapping(target = "variant", ignore = true)
    @Mapping(target = "totalCost", ignore = true)
    @Mapping(target = "avgCostBefore", ignore = true)
    @Mapping(target = "avgCostAfter", ignore = true)
    InventoryReceiptItem toItemEntity(StockReceiveItemRequest request);

    @Mapping(target = "variantId", source = "variant.id")
    @Mapping(target = "variantSku", source = "variant.sku")
    @Mapping(target = "variantName", source = "variant.name")
    StockReceiveItemResponse toItemResponse(InventoryReceiptItem item);
}
