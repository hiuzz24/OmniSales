package fu.osms.inventory.mapper;

import fu.osms.inventory.dto.request.StocktakeItemRequest;
import fu.osms.inventory.dto.request.StocktakeSessionRequest;
import fu.osms.inventory.dto.response.StocktakeItemResponse;
import fu.osms.inventory.dto.response.StocktakeSessionResponse;
import fu.osms.inventory.entity.StocktakeItem;
import fu.osms.inventory.entity.StocktakeSession;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface StocktakeMapper {

    @Mapping(target = "id", ignore = true)

    @Mapping(target = "warehouse", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "startedBy", ignore = true)
    @Mapping(target = "startedAt", ignore = true)
    @Mapping(target = "completedBy", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    @Mapping(target = "cancelledBy", ignore = true)
    @Mapping(target = "cancelledAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    StocktakeSession toEntity(StocktakeSessionRequest request);

    @Mapping(target = "warehouseId", source = "warehouse.id")
    @Mapping(target = "warehouseName", source = "warehouse.name")
    @Mapping(target = "warehouseAddress", source = "warehouse.address")
    @Mapping(target = "createdById", source = "createdBy.id")
    @Mapping(target = "createdByName", source = "createdBy.fullName")
    @Mapping(target = "startedById", source = "startedBy.id")
    @Mapping(target = "startedByName", source = "startedBy.fullName")
    @Mapping(target = "completedById", source = "completedBy.id")
    @Mapping(target = "completedByName", source = "completedBy.fullName")
    @Mapping(target = "cancelledById", source = "cancelledBy.id")
    @Mapping(target = "cancelledByName", source = "cancelledBy.fullName")
    @Mapping(target = "items", ignore = true)
    StocktakeSessionResponse toResponse(StocktakeSession session);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "session", ignore = true)
    @Mapping(target = "variant", ignore = true)
    @Mapping(target = "difference", ignore = true)
    StocktakeItem toItemEntity(StocktakeItemRequest request);

    @Mapping(target = "variantId", source = "variant.id")
    @Mapping(target = "variantSku", source = "variant.sku")
    @Mapping(target = "variantName", source = "variant.name")
    @Mapping(target = "productId", source = "variant.product.id")
    @Mapping(target = "productName", source = "variant.product.name")
    @Mapping(target = "barcode", source = "variant.barcode")
    @Mapping(target = "unit", source = "variant.product.unit")
    @Mapping(target = "costPrice", source = "variant.costPrice")
    StocktakeItemResponse toItemResponse(StocktakeItem item);
}
