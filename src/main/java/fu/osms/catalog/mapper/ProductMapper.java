package fu.osms.catalog.mapper;

import fu.osms.catalog.dto.request.ProductRequest;
import fu.osms.catalog.dto.response.ProductResponse;
import fu.osms.catalog.entity.Product;
import org.mapstruct.*;

@Mapper(componentModel = "spring",
        uses = {ProductVariantMapper.class, ProductImageMapper.class})
public interface ProductMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "shop", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    Product toEntity(ProductRequest request);

    @Mapping(target = "shopId", source = "shop.id")
    @Mapping(target = "shopName", source = "shop.name")
    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categoryName", source = "category.name")
    @Mapping(target = "createdById", source = "createdBy.id")
    @Mapping(target = "createdByName", source = "createdBy.fullName")
    @Mapping(target = "updatedById", source = "updatedBy.id")
    @Mapping(target = "updatedByName", source = "updatedBy.fullName")
    @Mapping(target = "variants", ignore = true)
    @Mapping(target = "images", ignore = true)
    ProductResponse toResponse(Product product);

    /**
     * Phiên bản đầy đủ: bao gồm variants và images.
     * Cần được gọi thủ công sau khi load các collection liên quan.
     */
    @Mapping(target = "shopId", source = "product.shop.id")
    @Mapping(target = "shopName", source = "product.shop.name")
    @Mapping(target = "categoryId", source = "product.category.id")
    @Mapping(target = "categoryName", source = "product.category.name")
    @Mapping(target = "createdById", source = "product.createdBy.id")
    @Mapping(target = "createdByName", source = "product.createdBy.fullName")
    @Mapping(target = "updatedById", source = "product.updatedBy.id")
    @Mapping(target = "updatedByName", source = "product.updatedBy.fullName")
    ProductResponse toResponseWithDetails(Product product);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "shop", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    void updateEntityFromRequest(ProductRequest request, @MappingTarget Product product);
}
