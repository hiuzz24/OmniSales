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

    // ── Session ───────────────────────────────────────────────────────────────

    @Mapping(target = "id", ignore = true)

    @Mapping(target = "warehouse", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    StocktakeSession toEntity(StocktakeSessionRequest request);

    @Mapping(target = "warehouseId", source = "warehouse.id")
    @Mapping(target = "warehouseName", source = "warehouse.name")
    @Mapping(target = "createdById", source = "createdBy.id")
    @Mapping(target = "createdByName", source = "createdBy.fullName")
    @Mapping(target = "items", ignore = true)
    StocktakeSessionResponse toResponse(StocktakeSession session);

    // ── Item ──────────────────────────────────────────────────────────────────

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "session", ignore = true)
    @Mapping(target = "variant", ignore = true)
    @Mapping(target = "difference", ignore = true)
    StocktakeItem toItemEntity(StocktakeItemRequest request);

    @Mapping(target = "variantId", source = "variant.id")
    @Mapping(target = "variantSku", source = "variant.sku")
    @Mapping(target = "variantName", source = "variant.name")
    StocktakeItemResponse toItemResponse(StocktakeItem item);
}
