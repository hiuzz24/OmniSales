package fu.osms.catalog.controller;

import fu.osms.catalog.dto.request.CategoryRequest;
import fu.osms.catalog.dto.response.CategoryResponse;
import fu.osms.catalog.service.CategoryService;
import fu.osms.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping
    public ResponseEntity<ApiResponse<CategoryResponse>> create(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo danh mục thành công", categoryService.create(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getById(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getByShop(@RequestParam UUID shopId) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getByShopId(shopId)));
    }

    @GetMapping("/roots")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getRoots(@RequestParam UUID shopId) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getRootCategories(shopId)));
    }

    @GetMapping("/{parentId}/children")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getChildren(@PathVariable UUID parentId) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getSubCategories(parentId)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> update(@PathVariable UUID id,
                                                                 @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật thành công", categoryService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        categoryService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Xoá danh mục thành công", null));
    }
}
