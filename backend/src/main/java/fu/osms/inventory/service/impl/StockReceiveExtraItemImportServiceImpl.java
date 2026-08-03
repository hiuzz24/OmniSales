package fu.osms.inventory.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.annotation.ExcelProperty;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.inventory.dto.request.ConfirmExtraItemRowDTO;
import fu.osms.inventory.dto.request.ConfirmExtraItemsRequest;
import fu.osms.inventory.dto.response.PreviewRowDTO;
import fu.osms.inventory.dto.response.ProductSuggestionDTO;
import fu.osms.inventory.dto.response.StockInImportResultDTO;
import fu.osms.inventory.entity.InventoryReceipt;
import fu.osms.inventory.entity.InventoryReceiptItem;
import fu.osms.inventory.repository.StockReceiveItemRepository;
import fu.osms.inventory.repository.StockReceiveRepository;
import fu.osms.inventory.service.StockReceiveExtraItemImportService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockReceiveExtraItemImportServiceImpl implements StockReceiveExtraItemImportService {
    private static final int TEMPLATE_MAX_ROWS = 1000;
    private static final int SUGGESTION_LIMIT = 3;
    private static final int BATCH_SIZE = 500;
    private static final String DUPLICATE_VARIANT_REASON = "Sản phẩm con này đã có trong phiếu nhập kho.";

    private record LookupRow(String productName, String variantName, String sku, String inputValue,
                             String variantId, BigDecimal defaultUnitPrice) { }

    private final StockReceiveRepository receiptRepository;
    private final StockReceiveItemRepository itemRepository;
    private final ProductVariantRepository variantRepository;
    private final ChannelProductVariantRepository channelProductVariantRepository;

    @Value("${app.inventory-extra-import.fuzzy-threshold:0.4}")
    private double fuzzyThreshold;
    @Value("${app.inventory-extra-import.error-directory:${java.io.tmpdir}/osms/inventory-extra-import-errors}")
    private String errorDirectory;

    @Override
    public byte[] createTemplate(UUID receiptId) throws IOException {
        requireDraftReceipt(receiptId);
        return buildTemplate();
    }

    @Override
    public byte[] createTemplateForNewReceipt() throws IOException {
        return buildTemplate();
    }

    private byte[] buildTemplate() throws IOException {
        List<ProductVariant> variants = importableVariants();
        List<TemplateCatalogRow> catalog = variants.stream()
                .map(v -> new TemplateCatalogRow(displayName(v)))
                .toList();
        List<LookupRow> lookupRows = variants.stream()
                .map(v -> new LookupRow(
                        v.getProduct().getName(), value(v.getName()), value(v.getSku()), displayName(v),
                        v.getId().toString(), v.getPrice() == null ? BigDecimal.ZERO : v.getPrice()))
                .toList();
        ByteArrayOutputStream easyExcelOutput = new ByteArrayOutputStream();
        EasyExcel.write(easyExcelOutput, TemplateInputRow.class).sheet("NhapLieu")
                .doWrite(List.of());
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(easyExcelOutput.toByteArray()));
             ByteArrayOutputStream result = new ByteArrayOutputStream()) {
            XSSFSheet input = workbook.getSheet("NhapLieu");
            XSSFSheet catalogSheet = workbook.createSheet("DanhMucSanPham");
            catalogSheet.createRow(0).createCell(0).setCellValue("Tên sản phẩm");
            for (int i = 0; i < catalog.size(); i++) catalogSheet.createRow(i + 1).createCell(0).setCellValue(catalog.get(i).name);
            XSSFSheet lookupSheet = workbook.createSheet("TraCuuSanPham");
            Row lookupHeader = lookupSheet.createRow(0);
            String[] lookupLabels = {"Tên sản phẩm", "Biến thể", "SKU", "Giá trị dùng để nhập", "Variant ID", "Đơn giá mặc định"};
            for (int column = 0; column < lookupLabels.length; column++) lookupHeader.createCell(column).setCellValue(lookupLabels[column]);
            for (int i = 0; i < lookupRows.size(); i++) {
                LookupRow row = lookupRows.get(i);
                Row excelRow = lookupSheet.createRow(i + 1);
                excelRow.createCell(0).setCellValue(row.productName);
                excelRow.createCell(1).setCellValue(row.variantName);
                excelRow.createCell(2).setCellValue(row.sku);
                excelRow.createCell(3).setCellValue(row.inputValue);
                excelRow.createCell(4).setCellValue(row.variantId);
                excelRow.createCell(5).setCellValue(row.defaultUnitPrice.doubleValue());
            }
            if (!lookupRows.isEmpty()) lookupSheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, lookupRows.size(), 0, 5));
            styleTemplate(workbook, input, lookupSheet, lookupRows.size());
            if (!catalog.isEmpty()) {
                DataValidationHelper helper = input.getDataValidationHelper();
                DataValidationConstraint constraint = helper.createFormulaListConstraint("'DanhMucSanPham'!$A$2:$A$" + (catalog.size() + 1));
                DataValidation validation = helper.createValidation(constraint, new CellRangeAddressList(1, TEMPLATE_MAX_ROWS, 0, 0));
                validation.setSuppressDropDownArrow(true);
                validation.setShowErrorBox(true);
                input.addValidationData(validation);
            }
            for (int rowIndex = 1; rowIndex <= TEMPLATE_MAX_ROWS; rowIndex++) {
                int excelRow = rowIndex + 1;
                input.getRow(rowIndex).getCell(2).setCellFormula(
                        "IFERROR(VLOOKUP(A" + excelRow + ",'TraCuuSanPham'!$D$2:$F$" + (lookupRows.size() + 1) + ",3,FALSE),\"\")");
                input.getRow(rowIndex).getCell(3).setCellFormula(
                        "IFERROR(VLOOKUP(A" + excelRow + ",'TraCuuSanPham'!$D$2:$F$" + (lookupRows.size() + 1) + ",2,FALSE),\"\")");
            }
            input.createFreezePane(0, 1);
            lookupSheet.createFreezePane(0, 1);
            input.setColumnHidden(3, true);
            workbook.setSheetHidden(workbook.getSheetIndex(catalogSheet), true);
            workbook.write(result);
            return result.toByteArray();
        }
    }

    private void styleTemplate(XSSFWorkbook workbook, XSSFSheet input, XSSFSheet lookupSheet, int lookupSize) {
        CellStyle header = workbook.createCellStyle();
        header.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        header.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);
        header.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);
        Font headerFont = workbook.createFont();
        headerFont.setBold(true); headerFont.setColor(IndexedColors.WHITE.getIndex()); header.setFont(headerFont);
        CellStyle entry = workbook.createCellStyle();
        entry.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        entry.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        entry.setWrapText(true); entry.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.TOP);
        entry.setBorderBottom(BorderStyle.THIN); entry.setBorderTop(BorderStyle.THIN);
        entry.setBorderLeft(BorderStyle.THIN); entry.setBorderRight(BorderStyle.THIN);
        for (int column = 0; column < 4; column++) input.getRow(0).getCell(column).setCellStyle(header);
        input.getRow(0).setHeightInPoints(30);
        input.setColumnWidth(0, 55 * 256); input.setColumnWidth(1, 14 * 256); input.setColumnWidth(2, 18 * 256);
        for (int rowIndex = 1; rowIndex <= TEMPLATE_MAX_ROWS; rowIndex++) {
            Row row = input.getRow(rowIndex); if (row == null) row = input.createRow(rowIndex);
            row.setHeightInPoints(34);
            for (int column = 0; column < 4; column++) {
                if (row.getCell(column) == null) row.createCell(column);
                row.getCell(column).setCellStyle(entry);
            }
        }
        for (int column = 0; column < 6; column++) lookupSheet.getRow(0).getCell(column).setCellStyle(header);
        lookupSheet.setColumnWidth(0, 45 * 256); lookupSheet.setColumnWidth(1, 25 * 256);
        lookupSheet.setColumnWidth(2, 20 * 256); lookupSheet.setColumnWidth(3, 60 * 256);
        lookupSheet.setColumnWidth(4, 40 * 256); lookupSheet.setColumnWidth(5, 18 * 256);
        for (int rowIndex = 1; rowIndex <= lookupSize; rowIndex++) lookupSheet.getRow(rowIndex).getCell(3).setCellStyle(entry);
    }

    @Override
    public List<PreviewRowDTO> preview(UUID receiptId, MultipartFile file) throws IOException {
        requireDraftReceipt(receiptId);
        if (file == null || file.isEmpty()) throw new AppException(ErrorCode.EXCEL_IMPORT_EMPTY);
        List<ExcelInputRow> rows;
        try (InputStream input = file.getInputStream()) {
            rows = EasyExcel.read(input).head(ExcelInputRow.class).sheet("NhapLieu").doReadSync();
        } catch (RuntimeException ex) {
            throw new AppException(ErrorCode.EXCEL_IMPORT_INVALID_FILE, "Khong the doc file Excel.", ex);
        }
        if (rows == null || rows.isEmpty()) throw new AppException(ErrorCode.EXCEL_IMPORT_EMPTY);
        List<ProductVariant> variants = importableVariants();
        List<PreviewRowDTO> output = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            ExcelInputRow row = rows.get(i);
            if (isBlank(row.name) && row.quantity == null && row.unitPrice == null) continue;
            output.add(toPreview(i + 2, row, variants));
        }
        if (output.isEmpty()) throw new AppException(ErrorCode.EXCEL_IMPORT_EMPTY);
        return output;
    }

    @Override
    @Transactional(rollbackFor = IOException.class)
    public StockInImportResultDTO confirm(UUID receiptId, ConfirmExtraItemsRequest request) throws IOException {
        InventoryReceipt receipt = requireDraftReceipt(receiptId);
        List<ConfirmExtraItemRowDTO> rows = request == null || request.getRows() == null ? List.of() : request.getRows();
        List<ErrorRow> errors = new ArrayList<>();
        List<InventoryReceiptItem> accepted = new ArrayList<>();
        Set<UUID> receiptVariantIds = itemRepository.findByReceiptId(receiptId).stream()
                .map(InventoryReceiptItem::getVariant)
                .filter(Objects::nonNull)
                .map(ProductVariant::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        BigDecimal extraTotal = BigDecimal.ZERO;
        for (ConfirmExtraItemRowDTO row : rows) {
            String reason = validateConfirm(row);
            ProductVariant variant = null;
            if (reason == null) {
                variant = variantRepository.findImportableById(row.getVariantId()).orElse(null);
                if (variant == null) reason = "San pham khong ton tai, da xoa hoac khong con ACTIVE.";
            }
            if (row != null && row.isSkipped()) reason = "Nguoi dung bo qua dong nay.";
            if (reason == null && receiptVariantIds.contains(variant.getId())) reason = DUPLICATE_VARIANT_REASON;
            if (reason != null) {
                errors.add(new ErrorRow(value(row == null ? null : row.getRawInputName()), row == null ? null : row.getQuantity(), row == null ? null : row.getUnitPrice(), reason));
                continue;
            }
            accepted.add(InventoryReceiptItem.builder().receipt(receipt).variant(variant)
                    .quantity(row.getQuantity()).unitCost(row.getUnitPrice()).build());
            receiptVariantIds.add(variant.getId());
            extraTotal = extraTotal.add(row.getUnitPrice().multiply(BigDecimal.valueOf(row.getQuantity())));
        }
        for (int i = 0; i < accepted.size(); i += BATCH_SIZE) {
            itemRepository.saveAll(accepted.subList(i, Math.min(i + BATCH_SIZE, accepted.size())));
            itemRepository.flush();
        }
        if (!accepted.isEmpty()) {
            receipt.setTotalCost((receipt.getTotalCost() == null ? BigDecimal.ZERO : receipt.getTotalCost()).add(extraTotal));
            receiptRepository.save(receipt);
        }
        String url = errors.isEmpty() ? null : writeErrorFile(errors);
        return StockInImportResultDTO.builder().successCount(accepted.size()).skippedCount(errors.size()).errorFileUrl(url).build();
    }

    @Override
    public Path resolveErrorFile(String fileName) {
        if (fileName == null || !fileName.matches("[a-zA-Z0-9._-]+\\.xlsx")) throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        Path root = Paths.get(errorDirectory).toAbsolutePath().normalize();
        Path file = root.resolve(fileName).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        return file;
    }

    private PreviewRowDTO toPreview(int rowIndex, ExcelInputRow row, List<ProductVariant> variants) {
        String raw = value(row.name);
        String validation = validatePreview(raw, row.quantity, row.unitPrice);
        if (validation != null) return PreviewRowDTO.builder().rowIndex(rowIndex).rawInputName(raw).quantity(row.quantity).unitPrice(row.unitPrice)
                .matchStatus("NOT_FOUND").reason(validation).suggestions(List.of()).build();
        String key = normalize(raw);
        Optional<ProductVariant> exact = variants.stream().filter(v -> exactMatch(v, raw, key)).findFirst();
        if (exact.isPresent()) return PreviewRowDTO.builder().rowIndex(rowIndex).rawInputName(raw).quantity(row.quantity).unitPrice(row.unitPrice)
                .matchStatus("EXACT_MATCH").matchedVariantId(exact.get().getId()).matchedProductName(displayName(exact.get()))
                .suggestions(suggestions(key, variants, exact.get().getId())).build();
        List<ProductSuggestionDTO> suggestions = suggestions(key, variants, null);
        return PreviewRowDTO.builder().rowIndex(rowIndex).rawInputName(raw).quantity(row.quantity).unitPrice(row.unitPrice)
                .matchStatus(suggestions.isEmpty() ? "NOT_FOUND" : "SUGGESTED").reason(suggestions.isEmpty() ? "Khong tim thay san pham phu hop." : null)
                .suggestions(suggestions).build();
    }

    private InventoryReceipt requireDraftReceipt(UUID receiptId) {
        InventoryReceipt receipt = receiptRepository.findById(receiptId).orElseThrow(() -> new AppException(ErrorCode.RECEIPT_NOT_FOUND));
        if (!"DRAFT".equals(receipt.getStatus())) throw new AppException(ErrorCode.RECEIPT_ALREADY_CONFIRMED);
        return receipt;
    }
    private List<ProductVariant> importableVariants() {
        List<ProductVariant> variants = variantRepository.findAllImportableWithProduct();
        if (variants.isEmpty()) {
            return variants;
        }

        Map<UUID, Set<String>> externalSkuKeysByVariantId = new HashMap<>();
        channelProductVariantRepository.findActiveByVariantIdInWithChannel(
                        variants.stream().map(ProductVariant::getId).toList())
                .forEach(mapping -> addExternalSkuKey(externalSkuKeysByVariantId, mapping));

        Map<String, ProductVariant> representativeByGroup = new LinkedHashMap<>();
        for (ProductVariant variant : variants) {
            Set<String> externalSkuKeys = externalSkuKeysByVariantId.getOrDefault(variant.getId(), Set.of());
            String groupKey = externalSkuKeys.size() == 1
                    ? "marketplace-sku:" + externalSkuKeys.iterator().next()
                    : "variant:" + variant.getId();
            representativeByGroup.putIfAbsent(groupKey, variant);
        }
        return new ArrayList<>(representativeByGroup.values());
    }

    private void addExternalSkuKey(Map<UUID, Set<String>> keysByVariantId,
                                   ChannelProductVariant mapping) {
        if (mapping.getVariant() == null || mapping.getVariant().getId() == null) {
            return;
        }
        String externalSku = normalize(mapping.getExternalSku());
        if (externalSku.isBlank()) {
            return;
        }
        keysByVariantId.computeIfAbsent(mapping.getVariant().getId(), ignored -> new LinkedHashSet<>())
                .add(externalSku);
    }
    private List<ProductSuggestionDTO> suggestions(String key, List<ProductVariant> variants, UUID excludedVariantId) {
        return variants.stream().filter(v -> excludedVariantId == null || !excludedVariantId.equals(v.getId()))
                .map(v -> ProductSuggestionDTO.builder().variantId(v.getId()).productName(displayName(v))
                        .score(suggestionScore(key, v)).build())
                .filter(s -> s.getScore() >= fuzzyThreshold).sorted(Comparator.comparing(ProductSuggestionDTO::getScore).reversed())
                .limit(SUGGESTION_LIMIT).toList();
    }
    private double suggestionScore(String key, ProductVariant variant) {
        String productName = normalize(variant.getProduct().getName());
        String variantName = normalize(variant.getName());
        String sku = normalize(variant.getSku());
        if (productName.contains(key) || variantName.contains(key) || sku.contains(key)) return 1D;
        return Math.max(jaroWinkler(key, productName), Math.max(jaroWinkler(key, variantName), jaroWinkler(key, sku)));
    }
    private boolean exactMatch(ProductVariant v, String raw, String normalized) {
        return raw.equalsIgnoreCase(value(v.getSku())) || raw.equalsIgnoreCase(v.getId().toString()) || normalized.equals(normalize(displayName(v)))
                || (!isBlank(v.getName()) && normalized.equals(normalize(v.getName())));
    }
    private String validatePreview(String name, Integer quantity, BigDecimal price) {
        if (isBlank(name)) return "Ten san pham khong duoc de trong.";
        if (quantity == null || quantity <= 0) return "So luong phai lon hon 0.";
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) return "Don gia phai lon hon hoac bang 0.";
        return null;
    }
    private String validateConfirm(ConfirmExtraItemRowDTO row) {
        if (row == null) return "Dong du lieu khong hop le.";
        if (row.isSkipped()) return "Nguoi dung bo qua dong nay.";
        if (row.getVariantId() == null) return "Chua chon san pham.";
        return validatePreview(value(row.getRawInputName()), row.getQuantity(), row.getUnitPrice());
    }
    private String writeErrorFile(List<ErrorRow> rows) throws IOException {
        Path directory = Paths.get(errorDirectory).toAbsolutePath().normalize(); Files.createDirectories(directory);
        String name = "stock-in-import-errors-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "-" + UUID.randomUUID() + ".xlsx";
        try (OutputStream output = Files.newOutputStream(directory.resolve(name))) { EasyExcel.write(output, ErrorRow.class).sheet("LoiImport").doWrite(rows); }
        return "/api/receipts/import-extra-items/errors/" + name;
    }
    private static String displayName(ProductVariant v) { return v.getProduct().getName() + (isBlank(v.getName()) ? "" : " - " + v.getName()) + " [" + value(v.getSku()) + "]"; }
    private static boolean isBlank(String value) { return value == null || value.trim().isEmpty(); }
    private static String value(String value) { return value == null ? "" : value.trim(); }
    private static String normalize(String value) { String s = Normalizer.normalize(value(value), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT); return s.replaceAll("\\s+", " ").trim(); }
    private static double jaroWinkler(String a, String b) { if (a.equals(b)) return 1; if (a.isEmpty() || b.isEmpty()) return 0; int range = Math.max(a.length(), b.length()) / 2 - 1, matches = 0; boolean[] ma = new boolean[a.length()], mb = new boolean[b.length()]; for (int i=0;i<a.length();i++) for(int j=Math.max(0,i-range);j<=Math.min(i+range,b.length()-1);j++) if(!mb[j]&&a.charAt(i)==b.charAt(j)){ma[i]=mb[j]=true;matches++;break;} if(matches==0)return 0; int k=0,t=0; for(int i=0;i<a.length();i++)if(ma[i]){while(!mb[k])k++;if(a.charAt(i)!=b.charAt(k))t++;k++;} double j=(matches/(double)a.length()+matches/(double)b.length()+(matches-t/2.0)/matches)/3; int p=0;while(p<Math.min(4,Math.min(a.length(),b.length()))&&a.charAt(p)==b.charAt(p))p++;return j+p*.1*(1-j); }

    @lombok.Data public static class ExcelInputRow { @ExcelProperty("Tên sản phẩm") private String name; @ExcelProperty("Số lượng") private Integer quantity; @ExcelProperty("Đơn giá") private BigDecimal unitPrice; @ExcelProperty("_Variant ID") private String variantId; }
    @lombok.AllArgsConstructor public static class TemplateCatalogRow { @ExcelProperty("Tên sản phẩm") private String name; }
    @lombok.AllArgsConstructor public static class TemplateInputRow { @ExcelProperty("Tên sản phẩm") private String name; @ExcelProperty("Số lượng") private Integer quantity; @ExcelProperty("Đơn giá") private BigDecimal unitPrice; @ExcelProperty("_Variant ID") private String variantId; }
    @lombok.AllArgsConstructor public static class ErrorRow { @ExcelProperty("Tên sản phẩm đã nhập") private String name; @ExcelProperty("Số lượng") private Integer quantity; @ExcelProperty("Đơn giá") private BigDecimal unitPrice; @ExcelProperty("Lý do") private String reason; }
}
