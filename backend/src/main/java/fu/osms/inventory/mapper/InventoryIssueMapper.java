package fu.osms.inventory.mapper;

import fu.osms.inventory.dto.request.InventoryIssueItemRequest;
import fu.osms.inventory.dto.request.InventoryIssueRequest;
import fu.osms.inventory.dto.response.InventoryIssueItemResponse;
import fu.osms.inventory.dto.response.InventoryIssueResponse;
import fu.osms.inventory.entity.InventoryIssue;
import fu.osms.inventory.entity.InventoryIssueItem;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface InventoryIssueMapper {

    @Mapping(target = "id", ignore = true)

    @Mapping(target = "warehouse", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "totalCost", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "approvedBy", ignore = true)
    @Mapping(target = "confirmedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    InventoryIssue toEntity(InventoryIssueRequest request);

    @Mapping(target = "warehouseId", source = "warehouse.id")
    @Mapping(target = "warehouseName", source = "warehouse.name")
    @Mapping(target = "createdById", source = "createdBy.id")
    @Mapping(target = "createdByName", source = "createdBy.fullName")
    @Mapping(target = "approvedById", source = "approvedBy.id")
    @Mapping(target = "approvedByName", source = "approvedBy.fullName")
    @Mapping(target = "items", ignore = true)
    InventoryIssueResponse toResponse(InventoryIssue issue);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "inventoryIssue", ignore = true)
    @Mapping(target = "productVariant", ignore = true)
    @Mapping(target = "totalCost", ignore = true)
    @Mapping(target = "isGift", constant = "false")
    InventoryIssueItem toItemEntity(InventoryIssueItemRequest request);

    @Mapping(target = "variantId", source = "productVariant.id")
    @Mapping(target = "variantSku", source = "productVariant.sku")
    @Mapping(target = "variantName", source = "productVariant.name")
    InventoryIssueItemResponse toItemResponse(InventoryIssueItem item);
}
