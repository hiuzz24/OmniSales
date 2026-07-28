package fu.osms.catalog.service.impl;

import fu.osms.catalog.dto.request.ProductRequest;
import fu.osms.catalog.dto.request.ProductVariantRequest;
import fu.osms.catalog.dto.response.ProductImportResult;
import fu.osms.catalog.entity.Category;
import fu.osms.catalog.entity.Product;
import fu.osms.catalog.enums.ProductStatus;
import fu.osms.catalog.repository.CategoryRepository;
import fu.osms.catalog.repository.ProductRepository;
import fu.osms.catalog.service.ProductImportService;
import fu.osms.catalog.service.ProductService;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductImportServiceImpl implements ProductImportService {

    private static final List<String> REQUIRED_COLUMNS = List.of(
            "productSku", "productName", "variantSku", "price"
    );

    private static final List<String> PREFERRED_SHEET_NAMES = List.of(
            "nhập liệu", "nhap lieu", "template", "import", "data"
    );

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductService productService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductImportResult importFromExcel(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.EXCEL_IMPORT_INVALID_FILE, "File rỗng hoặc không được cung cấp");
        }

        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        if (!filename.endsWith(".xlsx") && !filename.endsWith(".xls")) {
            throw new AppException(ErrorCode.EXCEL_IMPORT_INVALID_FILE, "Chỉ chấp nhận file .xlsx hoặc .xls");
        }

        List<ExcelRow> rows;
        try {
            rows = parseExcel(file);
        } catch (Exception e) {
            log.error("Failed to parse Excel file", e);
            throw new AppException(ErrorCode.EXCEL_IMPORT_INVALID_FILE,
                    "Không đọc được file Excel: " + e.getMessage(), e);
        }

        if (rows.isEmpty()) {
            throw new AppException(ErrorCode.EXCEL_IMPORT_EMPTY, "File Excel không có dữ liệu");
        }

        validateRows(rows);

        Map<String, List<ExcelRow>> grouped = groupByProductSku(rows);

        ProductImportResult result = ProductImportResult.builder()
                .totalRows(rows.size())
                .build();

        for (Map.Entry<String, List<ExcelRow>> entry : grouped.entrySet()) {
            String productSku = entry.getKey();
            List<ExcelRow> productRows = entry.getValue();
            ExcelRow first = productRows.get(0);

            ProductRequest request = buildProductRequest(productSku, first, productRows);

            try {
                productService.create(request);
                result.setCreatedCount(result.getCreatedCount() + 1);
                result.getCreatedSkus().add(productSku);
            } catch (AppException ex) {
                if (ex.getErrorCode() == ErrorCode.PRODUCT_SKU_CONFLICT) {
                    Product existing = productRepository.findFirstBySkuAndDeletedAtIsNull(productSku)
                            .orElseThrow(() -> ex);
                    productService.update(existing.getId(), request);
                    result.setUpdatedCount(result.getUpdatedCount() + 1);
                    result.getUpdatedSkus().add(productSku);
                } else {
                    throw ex;
                }
            }
        }

        log.info("Product import completed: totalRows={}, created={}, updated={}",
                result.getTotalRows(), result.getCreatedCount(), result.getUpdatedCount());
        return result;
    }

    private List<ExcelRow> parseExcel(MultipartFile file) throws IOException {
        List<ExcelRow> result = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            StringBuilder sheetLog = new StringBuilder();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                sheetLog.append("[").append(i).append("=").append(workbook.getSheetName(i)).append("] ");
            }
            log.info("Excel file contains {} sheet(s): {}", workbook.getNumberOfSheets(), sheetLog);

            Sheet sheet = pickDataSheet(workbook);
            if (sheet == null) {
                log.warn("pickDataSheet returned null");
                return result;
            }
            log.info("Selected sheet: '{}'", sheet.getSheetName());

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                log.warn("Sheet '{}' has no header row", sheet.getSheetName());
                return result;
            }

            DataFormatter formatter = new DataFormatter();
            StringBuilder headerLog = new StringBuilder();
            Map<Integer, String> columnIndexToKey = new LinkedHashMap<>();
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                Cell cell = headerRow.getCell(i);
                String headerName = cell == null ? "" : formatter.formatCellValue(cell).trim();
                String key = normalizeHeader(headerName);
                headerLog.append(String.format("col[%d]='%s' -> '%s'; ", i, headerName, key));
                if (!key.isEmpty()) {
                    columnIndexToKey.put(i, key);
                }
            }
            log.info("Header mapping: {}", headerLog);

            for (String required : REQUIRED_COLUMNS) {
                if (!columnIndexToKey.containsValue(required)) {
                    log.error("Missing required column '{}'. Headers parsed: {}", required, headerLog);
                    throw new AppException(ErrorCode.EXCEL_IMPORT_INVALID_FILE,
                            "Thiếu cột bắt buộc: " + required);
                }
            }

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                ExcelRow er = new ExcelRow();
                er.rowIndex = r + 1;

                for (Map.Entry<Integer, String> e : columnIndexToKey.entrySet()) {
                    Cell cell = row.getCell(e.getKey());
                    String value = cell == null ? "" : formatter.formatCellValue(cell).trim();
                    er.values.put(e.getValue(), value);
                }

                boolean allEmpty = er.values.values().stream().allMatch(v -> v == null || v.isEmpty());
                if (allEmpty) continue;

                result.add(er);
            }
        }
        return result;
    }

    private Sheet pickDataSheet(Workbook workbook) {
        if (workbook.getNumberOfSheets() == 0) return null;

        for (String preferred : PREFERRED_SHEET_NAMES) {
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet s = workbook.getSheetAt(i);
                if (s == null) continue;
                String name = s.getSheetName();
                if (name != null && name.trim().toLowerCase().equals(preferred)) {
                    return s;
                }
            }
        }

        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet s = workbook.getSheetAt(i);
            if (s == null) continue;
            Row header = s.getRow(0);
            if (header == null) continue;
            DataFormatter formatter = new DataFormatter();
            boolean hasRequired = false;
            for (int c = 0; c < header.getLastCellNum(); c++) {
                Cell cell = header.getCell(c);
                String name = cell == null ? "" : formatter.formatCellValue(cell).trim();
                String key = normalizeHeader(name);
                if (REQUIRED_COLUMNS.contains(key)) {
                    hasRequired = true;
                    break;
                }
            }
            if (hasRequired) return s;
        }

        return workbook.getSheetAt(0);
    }

    private String normalizeHeader(String header) {
        if (header == null) return "";
        String normalized = header.trim().toLowerCase();
        return switch (normalized) {
            case "mã sản phẩm (sku cha)", "sku cha", "productsku", "product sku" -> "productSku";
            case "tên sản phẩm", "tên sp", "productname", "product name" -> "productName";
            case "danh mục", "categoryname", "category name" -> "categoryName";
            case "thương hiệu", "brand" -> "brand";
            case "mô tả", "description" -> "description";
            case "đơn vị", "don vi", "unit" -> "unit";
            case "trạng thái", "trang thai", "status" -> "status";
            case "sku biến thể", "variantsku", "variant sku" -> "variantSku";
            case "tên biến thể", "variantname", "variant name" -> "variantName";
            case "giá bán", "giá", "price" -> "price";
            case "giá vốn", "gia von", "costprice", "cost price" -> "costPrice";
            case "barcode" -> "barcode";
            case "trọng lượng (g)", "trọng lượng", "weightgrams", "weight grams" -> "weightGrams";
            default -> normalized;
        };
    }

    private void validateRows(List<ExcelRow> rows) {
        List<String> errors = new ArrayList<>();
        Map<String, Integer> productSkuRowCount = new HashMap<>();

        for (ExcelRow r : rows) {
            String pSku = r.values.getOrDefault("productSku", "");
            String pName = r.values.getOrDefault("productName", "");
            String vSku = r.values.getOrDefault("variantSku", "");
            String priceStr = r.values.getOrDefault("price", "");

            if (pSku.isEmpty()) {
                errors.add("Dòng " + r.rowIndex + ": thiếu Mã sản phẩm (SKU cha)");
            }
            if (pName.isEmpty()) {
                errors.add("Dòng " + r.rowIndex + ": thiếu Tên sản phẩm");
            }
            if (vSku.isEmpty()) {
                errors.add("Dòng " + r.rowIndex + ": thiếu SKU biến thể");
            }
            if (!priceStr.isEmpty()) {
                try {
                    BigDecimal price = new BigDecimal(priceStr.replace(",", ""));
                    if (price.compareTo(BigDecimal.ZERO) < 0) {
                        errors.add("Dòng " + r.rowIndex + ": Giá bán không được âm");
                    }
                } catch (NumberFormatException ex) {
                    errors.add("Dòng " + r.rowIndex + ": Giá bán không đúng định dạng số");
                }
            }

            String costPriceStr = r.values.getOrDefault("costPrice", "");
            if (!costPriceStr.isEmpty()) {
                try {
                    BigDecimal costPrice = new BigDecimal(costPriceStr.replace(",", ""));
                    if (costPrice.compareTo(BigDecimal.ZERO) < 0) {
                        errors.add("Dòng " + r.rowIndex + ": Giá vốn không được âm");
                    }
                } catch (NumberFormatException ex) {
                    errors.add("Dòng " + r.rowIndex + ": Giá vốn không đúng định dạng số");
                }
            }

            String status = r.values.getOrDefault("status", "");
            if (!status.isEmpty()) {
                boolean valid = false;
                for (ProductStatus ps : ProductStatus.values()) {
                    if (ps.name().equalsIgnoreCase(status)) { valid = true; break; }
                }
                if (!valid) {
                    errors.add("Dòng " + r.rowIndex + ": Trạng thái không hợp lệ (ACTIVE/INACTIVE/DRAFT)");
                }
            }

            if (!pSku.isEmpty()) {
                productSkuRowCount.merge(pSku, 1, Integer::sum);
            }
        }

        for (Map.Entry<String, Integer> e : productSkuRowCount.entrySet()) {
            if (e.getValue() < 1) {
                errors.add("Sản phẩm SKU='" + e.getKey() + "' phải có ít nhất 1 biến thể");
            }
        }

        if (!errors.isEmpty()) {
            throw new AppException(ErrorCode.EXCEL_IMPORT_VALIDATION_FAILED,
                    "Dữ liệu Excel không hợp lệ:\n- " + String.join("\n- ", errors));
        }
    }

    private Map<String, List<ExcelRow>> groupByProductSku(List<ExcelRow> rows) {
        Map<String, List<ExcelRow>> grouped = new LinkedHashMap<>();
        for (ExcelRow r : rows) {
            String pSku = r.values.get("productSku");
            grouped.computeIfAbsent(pSku, k -> new ArrayList<>()).add(r);
        }
        return grouped;
    }

    private ProductRequest buildProductRequest(String productSku, ExcelRow first, List<ExcelRow> allRows) {
        String categoryName = first.values.getOrDefault("categoryName", "");

        List<ProductVariantRequest> variants = new ArrayList<>();
        for (ExcelRow r : allRows) {
            ProductVariantRequest vr = new ProductVariantRequest();
            vr.setSku(r.values.getOrDefault("variantSku", ""));
            vr.setName(emptyToNull(r.values.get("variantName")));
            vr.setBarcode(emptyToNull(r.values.get("barcode")));

            String priceStr = r.values.getOrDefault("price", "").replace(",", "");
            if (!priceStr.isEmpty()) {
                try {
                    vr.setPrice(new BigDecimal(priceStr));
                } catch (NumberFormatException ex) {
                    vr.setPrice(BigDecimal.ZERO);
                }
            }

            String costPriceStr = r.values.getOrDefault("costPrice", "").replace(",", "");
            if (!costPriceStr.isEmpty()) {
                try {
                    vr.setCostPrice(new BigDecimal(costPriceStr));
                } catch (NumberFormatException ex) {
                    // ignore invalid cost price
                }
            }

            String weightStr = r.values.getOrDefault("weightGrams", "");
            if (!weightStr.isEmpty()) {
                try { vr.setWeightGrams(Integer.parseInt(weightStr)); } catch (NumberFormatException ignore) {}
            }

            if (vr.getOptionValues() == null) {
                vr.setOptionValues(new HashMap<>());
            }
            vr.setIsActive(true);

            variants.add(vr);
        }

        ProductRequest request = new ProductRequest();
        request.setSku(productSku);
        request.setName(first.values.getOrDefault("productName", ""));
        request.setDescription(emptyToNull(first.values.get("description")));
        request.setBrand(emptyToNull(first.values.get("brand")));
        request.setUnit(emptyToNull(first.values.get("unit")));

        String statusStr = first.values.getOrDefault("status", "");
        if (!statusStr.isEmpty()) {
            try {
                request.setStatus(ProductStatus.valueOf(statusStr.toUpperCase()));
            } catch (IllegalArgumentException ex) {
                request.setStatus(ProductStatus.DRAFT);
            }
        } else {
            request.setStatus(ProductStatus.DRAFT);
        }

        request.setLowStockThreshold(5);
        request.setWeightGrams(firstPositiveWeightGrams(allRows));
        request.setAttributes(new HashMap<>());
        request.setVariants(variants);
        request.setImages(new ArrayList<>());
        request.setChannelIds(new ArrayList<>());

        if (!categoryName.isEmpty()) {
            Category category = categoryRepository.findFirstByNameIgnoreCase(categoryName)
                    .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND,
                            "Không tìm thấy danh mục: " + categoryName));
            request.setCategoryId(category.getId());
        } else {
            throw new AppException(ErrorCode.INVALID_REQUEST,
                    "Sản phẩm SKU='" + productSku + "' thiếu cột Danh mục (bắt buộc)");
        }

        return request;
    }

    private String emptyToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }

    private Integer firstPositiveWeightGrams(List<ExcelRow> rows) {
        if (rows == null) return null;
        for (ExcelRow row : rows) {
            String weightStr = row.values.getOrDefault("weightGrams", "");
            if (weightStr.isBlank()) continue;
            try {
                int weightGrams = Integer.parseInt(weightStr);
                if (weightGrams > 0) return weightGrams;
            } catch (NumberFormatException ignore) {
                // Invalid rows are handled by product validation after request mapping.
            }
        }
        return null;
    }

    private static class ExcelRow {
        int rowIndex;
        Map<String, String> values = new HashMap<>();
    }
}
