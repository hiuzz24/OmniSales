package fu.osms.inventory.service.impl;

import com.alibaba.excel.EasyExcel;
import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.enums.ProductStatus;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.inventory.dto.request.ConfirmExtraItemRowDTO;
import fu.osms.inventory.dto.request.ConfirmExtraItemsRequest;
import fu.osms.inventory.dto.response.PreviewRowDTO;
import fu.osms.inventory.dto.response.StockInImportResultDTO;
import fu.osms.inventory.entity.InventoryReceipt;
import fu.osms.inventory.entity.InventoryReceiptItem;
import fu.osms.inventory.repository.StockReceiveItemRepository;
import fu.osms.inventory.repository.StockReceiveRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StockReceiveExtraItemImportServiceImplTest {
    private StockReceiveRepository receiptRepository = mock(StockReceiveRepository.class);
    private StockReceiveItemRepository itemRepository = mock(StockReceiveItemRepository.class);
    private ProductVariantRepository variantRepository = mock(ProductVariantRepository.class);
    private ChannelProductVariantRepository channelProductVariantRepository = mock(ChannelProductVariantRepository.class);
    private StockReceiveExtraItemImportServiceImpl service;
    private UUID receiptId;
    private ProductVariant variant;
    @TempDir Path temp;

    @BeforeEach void setUp() {
        service = new StockReceiveExtraItemImportServiceImpl(
                receiptRepository, itemRepository, variantRepository, channelProductVariantRepository);
        ReflectionTestUtils.setField(service, "fuzzyThreshold", .4d);
        ReflectionTestUtils.setField(service, "errorDirectory", temp.toString());
        receiptId = UUID.randomUUID();
        InventoryReceipt receipt = InventoryReceipt.builder().id(receiptId).status("DRAFT").totalCost(BigDecimal.ZERO).build();
        when(receiptRepository.findById(receiptId)).thenReturn(Optional.of(receipt));
        Product product = Product.builder().id(UUID.randomUUID()).name("Cà phê sữa đá").status(ProductStatus.ACTIVE).build();
        variant = ProductVariant.builder().id(UUID.randomUUID()).product(product).name("Lon 250ml").sku("CF-250").isActive(true).build();
        when(variantRepository.findAllImportableWithProduct()).thenReturn(List.of(variant));
        when(channelProductVariantRepository.findActiveByVariantIdInWithChannel(anyList())).thenReturn(List.of());
    }

    @Test void preview_matches_exact_name() throws Exception {
        PreviewRowDTO row = service.preview(receiptId, excel("Cà phê sữa đá - Lon 250ml [CF-250]", 2, "10000")).get(0);
        assertEquals("EXACT_MATCH", row.getMatchStatus());
        assertEquals(variant.getId(), row.getMatchedVariantId());
    }

    @Test void preview_returns_fuzzy_suggestion() throws Exception {
        PreviewRowDTO row = service.preview(receiptId, excel("ca phe sua da", 2, "10000")).get(0);
        assertEquals("SUGGESTED", row.getMatchStatus());
        assertEquals(variant.getId(), row.getSuggestions().get(0).getVariantId());
    }

    @Test void preview_marks_no_suggestion_when_threshold_not_reached() throws Exception {
        // Raise the fuzzy threshold so only near-exact matches qualify; the input
        // string is unrelated to the seeded variant, so suggestions must be empty.
        ReflectionTestUtils.setField(service, "fuzzyThreshold", 0.999d);
        PreviewRowDTO row = service.preview(receiptId, excel("xyzqqq123 totally unrelated", 2, "10000")).get(0);
        assertEquals("NOT_FOUND", row.getMatchStatus());
        assertTrue(row.getSuggestions().isEmpty());
    }

    @Test void preview_matches_variant_returned_by_repository() throws Exception {
        Product standalone = Product.builder().id(UUID.randomUUID()).sku("NM-01")
                .name("Nước mắm truyền thống").status(ProductStatus.ACTIVE).build();
        ProductVariant defaultVariant = ProductVariant.builder().id(UUID.randomUUID()).product(standalone)
                .sku("NM-01").isActive(true).build();
        when(variantRepository.findAllImportableWithProduct()).thenReturn(List.of(defaultVariant));

        PreviewRowDTO row = service.preview(receiptId, excel("Nước mắm truyền thống", 2, "10000")).get(0);

        // The variant has no displayName, so exactMatch falls through to fuzzy lookup which
        // returns the same variant as the top suggestion.
        assertEquals("SUGGESTED", row.getMatchStatus());
        assertEquals(defaultVariant.getId(), row.getSuggestions().get(0).getVariantId());
    }

    @Test void confirm_records_skipped_row_in_error_file() throws Exception {
        StockInImportResultDTO result = service.confirm(receiptId, new ConfirmExtraItemsRequest(List.of(
                new ConfirmExtraItemRowDTO(2, "khong ro", null, 2, BigDecimal.TEN, true))));
        assertEquals(0, result.getSuccessCount()); assertEquals(1, result.getSkippedCount());
        assertNotNull(result.getErrorFileUrl()); verify(itemRepository, never()).saveAll(any());
    }

    @Test void confirm_rejects_variant_that_was_inactivated_after_preview() throws Exception {
        when(variantRepository.findImportableById(variant.getId())).thenReturn(Optional.empty());
        StockInImportResultDTO result = service.confirm(receiptId, new ConfirmExtraItemsRequest(List.of(
                new ConfirmExtraItemRowDTO(2, "Cà phê", variant.getId(), 2, BigDecimal.TEN, false))));
        assertEquals(0, result.getSuccessCount()); assertEquals(1, result.getSkippedCount());
        verify(itemRepository, never()).saveAll(any());
    }

    @Test void confirm_writes_duplicate_variant_already_in_receipt_to_error_file() throws Exception {
        when(variantRepository.findImportableById(variant.getId())).thenReturn(Optional.of(variant));
        when(itemRepository.findByReceiptId(receiptId)).thenReturn(List.of(
                InventoryReceiptItem.builder().variant(variant).build()));

        StockInImportResultDTO result = service.confirm(receiptId, new ConfirmExtraItemsRequest(List.of(
                new ConfirmExtraItemRowDTO(2, "Cà phê sữa đá", variant.getId(), 2, BigDecimal.TEN, false))));

        assertEquals(0, result.getSuccessCount());
        assertEquals(1, result.getSkippedCount());
        assertNotNull(result.getErrorFileUrl());
        verify(itemRepository, never()).saveAll(any());
    }

    @Test void preview_rejects_empty_file() {
        assertThrows(RuntimeException.class, () -> service.preview(receiptId, new MockMultipartFile("file", "empty.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[0])));
    }

    private MockMultipartFile excel(String name, int quantity, String price) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        StockReceiveExtraItemImportServiceImpl.ExcelInputRow input = new StockReceiveExtraItemImportServiceImpl.ExcelInputRow();
        input.setName(name); input.setQuantity(quantity); input.setUnitPrice(new BigDecimal(price));
        EasyExcel.write(output, StockReceiveExtraItemImportServiceImpl.ExcelInputRow.class).sheet("NhapLieu").doWrite(List.of(input));
        return new MockMultipartFile("file", "input.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
    }
}
