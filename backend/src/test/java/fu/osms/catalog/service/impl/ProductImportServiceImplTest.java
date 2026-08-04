package fu.osms.catalog.service.impl;

import fu.osms.catalog.dto.response.ProductImportResult;
import fu.osms.catalog.entity.Category;
import fu.osms.catalog.entity.Product;
import fu.osms.catalog.enums.ProductStatus;
import fu.osms.catalog.repository.CategoryRepository;
import fu.osms.catalog.repository.ProductRepository;
import fu.osms.catalog.service.ProductService;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductImportServiceImpl Tests")
class ProductImportServiceImplTest {

    @Mock private ProductRepository productRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ProductService productService;

    private ProductImportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProductImportServiceImpl(productRepository, categoryRepository, productService);
        // Default: a found Category for "Coffee" so happy-path tests don't trip CATEGORY_NOT_FOUND.
        Category coffee = Category.builder().id(UUID.randomUUID()).name("Coffee").build();
        lenient().when(categoryRepository.findFirstByNameIgnoreCase("Coffee"))
                .thenReturn(Optional.of(coffee));
    }

    private MultipartFile buildExcel(String sheetName, String[][] rows) throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet(sheetName);
            if (rows.length == 0) return toFile(wb, sheetName);
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r);
                String[] cells = rows[r];
                for (int c = 0; c < cells.length; c++) {
                    Cell cell = row.createCell(c);
                    cell.setCellValue(cells[c] == null ? "" : cells[c]);
                }
            }
            return toFile(wb, sheetName);
        }
    }

    private MultipartFile toFile(Workbook wb, String name) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        wb.write(baos);
        return new MockMultipartFile("file", name + ".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", baos.toByteArray());
    }

    /** Sample header in English + 2 rows. */
    private MultipartFile buildSampleExcel() throws Exception {
        return buildExcel("data", new String[][]{
                {"productSku", "productName", "categoryName", "variantSku", "price", "description"},
                {"SKU-001", "Espresso Beans 1kg", "Coffee", "VAR-001", "150000", "Premium dark roast"},
                {"SKU-002", "Vietnamese Coffee", "Coffee", "VAR-002", "99000", "Robusta beans"}
        });
    }

    @Test
    @DisplayName("importFromExcel: null file throws AppException with EXCEL_IMPORT_INVALID_FILE")
    void import_nullFile_throws() {
        assertThatThrownBy(() -> service.importFromExcel(null))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.EXCEL_IMPORT_INVALID_FILE);
    }

    @Test
    @DisplayName("importFromExcel: empty multipart file throws AppException")
    void import_emptyFile_throws() throws Exception {
        MultipartFile empty = new MockMultipartFile("file", "empty.xlsx", "application/vnd.openxmlformats", new byte[0]);
        assertThatThrownBy(() -> service.importFromExcel(empty))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.EXCEL_IMPORT_INVALID_FILE);
    }

    @Test
    @DisplayName("importFromExcel: wrong file extension throws AppException")
    void import_wrongExtension_throws() {
        MultipartFile wrong = new MockMultipartFile(
                "file", "doc.txt", "text/plain", "hello".getBytes());
        assertThatThrownBy(() -> service.importFromExcel(wrong))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.EXCEL_IMPORT_INVALID_FILE);
    }

    @Test
    @DisplayName("importFromExcel: missing a required column throws AppException with informative message")
    void import_missingRequiredColumn_throws() throws Exception {
        // Missing variantSku
        MultipartFile file = buildExcel("data", new String[][]{
                {"productSku", "productName", "price"},
                {"SKU-001", "Espresso Beans 1kg", "150000"}
        });

        assertThatThrownBy(() -> service.importFromExcel(file))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.EXCEL_IMPORT_INVALID_FILE);
    }

    @Test
    @DisplayName("importFromExcel: header-only file produces EXCEL_IMPORT_EMPTY")
    void import_headerOnly_throws() throws Exception {
        MultipartFile file = buildExcel("data", new String[][]{
                {"productSku", "productName", "variantSku", "price"}
        });

        assertThatThrownBy(() -> service.importFromExcel(file))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.EXCEL_IMPORT_EMPTY);
    }

    @Test
    @DisplayName("importFromExcel: row missing productName produces validation error list with 'Tên sản phẩm' message")
    void import_rowMissingProductName_throwsValidation() throws Exception {
        MultipartFile file = buildExcel("data", new String[][]{
                {"productSku", "productName", "variantSku", "price"},
                {"SKU-001", "", "VAR-001", "150000"}
        });

        assertThatThrownBy(() -> service.importFromExcel(file))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Tên sản phẩm");
    }

    @Test
    @DisplayName("importFromExcel: row with negative price reports the value error")
    void import_negativePrice_throwsValidation() throws Exception {
        MultipartFile file = buildExcel("data", new String[][]{
                {"productSku", "productName", "variantSku", "price"},
                {"SKU-001", "Bean 1kg", "VAR-001", "-10"}
        });

        assertThatThrownBy(() -> service.importFromExcel(file))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Giá bán không được âm");
    }

    @Test
    @DisplayName("importFromExcel: happy path with 2 unique SKUs creates 2 products and reports totalRows")
    void import_happyPath_createsProducts() throws Exception {
        MultipartFile file = buildSampleExcel();

        ProductImportResult result = service.importFromExcel(file);

        assertThat(result.getTotalRows()).isEqualTo(2);
        assertThat(result.getCreatedCount()).isEqualTo(2);
        assertThat(result.getUpdatedCount()).isZero();
        assertThat(result.getCreatedSkus()).containsExactlyInAnyOrder("SKU-001", "SKU-002");
        verify(productService, times(2)).create(any());
    }

    @Test
    @DisplayName("importFromExcel: SKU conflict (ProductSku conflict) — falls back to update path")
    void import_skuConflict_updatesExisting() throws Exception {
        UUID productId = UUID.randomUUID();
        MultipartFile file = buildSampleExcel();

        when(productService.create(any()))
                .thenThrow(new AppException(ErrorCode.PRODUCT_SKU_CONFLICT, "Product SKU already exists"));
        Product existing = Product.builder().id(productId).sku("SKU-001").status(ProductStatus.ACTIVE).build();
        when(productRepository.findFirstBySkuAndDeletedAtIsNull("SKU-001"))
                .thenReturn(Optional.of(existing));
        when(productRepository.findFirstBySkuAndDeletedAtIsNull("SKU-002"))
                .thenReturn(Optional.of(Product.builder().id(UUID.randomUUID()).sku("SKU-002").build()));

        ProductImportResult result = service.importFromExcel(file);

        assertThat(result.getCreatedCount()).isZero();
        assertThat(result.getUpdatedCount()).isEqualTo(2);
        assertThat(result.getUpdatedSkus()).containsExactlyInAnyOrder("SKU-001", "SKU-002");
        verify(productService, times(2)).update(any(UUID.class), any());
    }

    @Test
    @DisplayName("importFromExcel: non-conflict AppException is propagated (not swallowed)")
    void import_otherAppException_rethrows() throws Exception {
        MultipartFile file = buildSampleExcel();
        when(productService.create(any()))
                .thenThrow(new AppException(ErrorCode.PRODUCT_NOT_FOUND, "different failure"));

        assertThatThrownBy(() -> service.importFromExcel(file))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("importFromExcel: accepts a 'template' preferred sheet name and still imports")
    void import_picksPreferredSheet() throws Exception {
        // First sheet 'intro' has no data, second 'template' has the data.
        MultipartFile intro = buildExcel("intro", new String[][]{
                {"Welcome", "Please use the template sheet"}
        });
        MultipartFile templateSheet = buildExcel("template", new String[][]{
                {"productSku", "productName", "categoryName", "variantSku", "price"},
                {"SKU-A", "Bean A", "Coffee", "VAR-A", "100"}
        });

        // Build a workbook with both sheets:
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet s1 = wb.createSheet("intro");
        Row r1 = s1.createRow(0);
        r1.createCell(0).setCellValue("Welcome");
        Sheet s2 = wb.createSheet("template");
        Row h2 = s2.createRow(0);
        h2.createCell(0).setCellValue("productSku");
        h2.createCell(1).setCellValue("productName");
        h2.createCell(2).setCellValue("categoryName");
        h2.createCell(3).setCellValue("variantSku");
        h2.createCell(4).setCellValue("price");
        Row r2 = s2.createRow(1);
        r2.createCell(0).setCellValue("SKU-A");
        r2.createCell(1).setCellValue("Bean A");
        r2.createCell(2).setCellValue("Coffee");
        r2.createCell(3).setCellValue("VAR-A");
        r2.createCell(4).setCellValue("100");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        wb.write(baos);
        MultipartFile file = new MockMultipartFile(
                "file", "two-sheets.xlsx", "application/vnd.openxmlformats", baos.toByteArray());

        ProductImportResult result = service.importFromExcel(file);

        assertThat(result.getCreatedCount()).isEqualTo(1);
        assertThat(result.getCreatedSkus()).contains("SKU-A");
    }

    @Test
    @DisplayName("importFromExcel: skips rows where every cell is empty")
    void import_skipsCompletelyEmptyRows() throws Exception {
        MultipartFile file = buildExcel("data", new String[][]{
                {"productSku", "productName", "categoryName", "variantSku", "price"},
                {"SKU-001", "Coffee", "Coffee", "VAR-001", "100"},
                {"", "", "", "", ""}, // fully empty row should be skipped
                {"SKU-002", "Tea", "Coffee", "VAR-002", "200"}
        });

        ProductImportResult result = service.importFromExcel(file);

        assertThat(result.getTotalRows()).isEqualTo(2);
    }
}
