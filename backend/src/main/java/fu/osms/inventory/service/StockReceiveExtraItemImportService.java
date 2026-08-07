package fu.osms.inventory.service;

import fu.osms.inventory.dto.request.ConfirmExtraItemsRequest;
import fu.osms.inventory.dto.response.PreviewRowDTO;
import fu.osms.inventory.dto.response.StockInImportResultDTO;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public interface StockReceiveExtraItemImportService {
    byte[] createTemplate(UUID receiptId) throws IOException;
    byte[] createTemplateForNewReceipt() throws IOException;
    List<PreviewRowDTO> preview(UUID receiptId, MultipartFile file) throws IOException;
    StockInImportResultDTO confirm(UUID receiptId, ConfirmExtraItemsRequest request) throws IOException;
    Path resolveErrorFile(String fileName);
}
