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

    @Mapping(target = "id", ignore = true)

    @Mapping(target = "warehouse", ignore = true)
    @Mapping(target = "supplier", ignore = true)
    @Mapping(target = "purchaseOrder", ignore = true)
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
    @Mapping(target = "warehouseAddress", source = "warehouse.address")
    @Mapping(target = "supplierId", source = "supplier.id")
    @Mapping(target = "supplierName", source = "supplier.name")
    @Mapping(target = "purchaseOrderId", source = "purchaseOrder.id")
    @Mapping(target = "purchaseOrderCode", source = "purchaseOrder.orderCode")
    @Mapping(target = "createdById", source = "createdBy.id")
    @Mapping(target = "createdByName", source = "createdBy.fullName")
    @Mapping(target = "approvedById", source = "approvedBy.id")
    @Mapping(target = "approvedByName", source = "approvedBy.fullName")
    @Mapping(target = "items", ignore = true)
    @Mapping(target = "marketplacePlatforms", ignore = true)
    @Mapping(target = "marketplaceSyncAvailable", ignore = true)
    StockReceiveResponse toResponse(InventoryReceipt receipt);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "receipt", ignore = true)
    @Mapping(target = "variant", ignore = true)
    @Mapping(target = "totalCost", ignore = true)
    @Mapping(target = "avgCostBefore", ignore = true)
    @Mapping(target = "avgCostAfter", ignore = true)
    InventoryReceiptItem toItemEntity(StockReceiveItemRequest request);

    @Mapping(target = "variantId", source = "variant.id")
    @Mapping(target = "productName", source = "variant.product.name")
    @Mapping(target = "sku", source = "variant.sku")
    @Mapping(target = "marketplaceSku", ignore = true)
    @Mapping(target = "variantSku", source = "variant.sku")
    @Mapping(target = "variantName", source = "variant.name")
    @Mapping(target = "platforms", ignore = true)
    StockReceiveItemResponse toItemResponse(InventoryReceiptItem item);
}
