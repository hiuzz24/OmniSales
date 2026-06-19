package fu.osms.catalog.service;

import fu.osms.catalog.dto.response.ProductImportResult;
import org.springframework.web.multipart.MultipartFile;

public interface ProductImportService {

    ProductImportResult importFromExcel(MultipartFile file);
}
