package fu.osms.catalog.service;

import fu.osms.catalog.dto.request.CategorySuggestionRequest;

public interface CategorySuggestionInputService {

    CategorySuggestionRequest resolve(CategorySuggestionRequest request);
}
