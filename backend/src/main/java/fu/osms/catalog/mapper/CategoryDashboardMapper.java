package fu.osms.catalog.mapper;

import fu.osms.catalog.dto.response.CategoryResponseDTO;
import fu.osms.catalog.entity.Category;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CategoryDashboardMapper {

    @Mapping(target = "id", source = "category.id")
    @Mapping(target = "name", source = "category.name")
    @Mapping(target = "slug", source = "category.slug")
    @Mapping(target = "sortOrder", source = "category.sortOrder")
    @Mapping(target = "status", source = "category.status")
    @Mapping(
            target = "parentCategoryName",
            expression = "java(category.getParent() == null ? \"Danh mục gốc\" : category.getParent().getName())"
    )
    @Mapping(target = "productCount", source = "productCount")
    @Mapping(target = "createdAt", source = "category.createdAt")
    CategoryResponseDTO toResponseDTO(Category category, long productCount);

}