package fu.osms.catalog.mapper;

import fu.osms.catalog.dto.request.ProductImageRequest;
import fu.osms.catalog.dto.response.ProductImageResponse;
import fu.osms.catalog.entity.ProductImage;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface ProductImageMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "variant", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    ProductImage toEntity(ProductImageRequest request);

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "variantId", source = "variant.id")
    ProductImageResponse toResponse(ProductImage image);
}
