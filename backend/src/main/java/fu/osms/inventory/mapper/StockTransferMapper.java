package fu.osms.inventory.mapper;

import fu.osms.inventory.dto.request.StockTransferItemRequest;
import fu.osms.inventory.dto.request.StockTransferRequest;
import fu.osms.inventory.dto.response.StockTransferItemResponse;
import fu.osms.inventory.dto.response.StockTransferResponse;
import fu.osms.inventory.entity.StockTransfer;
import fu.osms.inventory.entity.StockTransferItem;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface StockTransferMapper {

    @Mapping(target = "id", ignore = true)

    @Mapping(target = "fromWarehouse", ignore = true)
    @Mapping(target = "toWarehouse", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "approvedBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    StockTransfer toEntity(StockTransferRequest request);

    @Mapping(target = "fromWarehouseId", source = "fromWarehouse.id")
    @Mapping(target = "fromWarehouseName", source = "fromWarehouse.name")
    @Mapping(target = "toWarehouseId", source = "toWarehouse.id")
    @Mapping(target = "toWarehouseName", source = "toWarehouse.name")
    @Mapping(target = "createdById", source = "createdBy.id")
    @Mapping(target = "createdByName", source = "createdBy.fullName")
    @Mapping(target = "approvedById", source = "approvedBy.id")
    @Mapping(target = "approvedByName", source = "approvedBy.fullName")
    @Mapping(target = "items", ignore = true)
    StockTransferResponse toResponse(StockTransfer transfer);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "transfer", ignore = true)
    @Mapping(target = "variant", ignore = true)
    StockTransferItem toItemEntity(StockTransferItemRequest request);

    @Mapping(target = "variantId", source = "variant.id")
    @Mapping(target = "variantSku", source = "variant.sku")
    @Mapping(target = "variantName", source = "variant.name")
    StockTransferItemResponse toItemResponse(StockTransferItem item);
}
