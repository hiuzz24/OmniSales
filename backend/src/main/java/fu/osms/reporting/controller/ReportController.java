package fu.osms.reporting.controller;

import fu.osms.common.dto.ApiResponse;
import fu.osms.reporting.dto.ChannelOrderReportResponse;
import fu.osms.reporting.service.ChannelOrderReportService;
import fu.osms.reporting.dto.ReturnReportResponse;
import fu.osms.reporting.service.ReturnReportService;
import fu.osms.reporting.dto.ProductReportResponse;
import fu.osms.reporting.service.ProductReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ChannelOrderReportService channelOrderReportService;
    private final ReturnReportService returnReportService;
    private final ProductReportService productReportService;

    @GetMapping("/orders-by-channel")
    public ResponseEntity<ApiResponse<ChannelOrderReportResponse>> getOrdersByChannel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(channelOrderReportService.getReport(from, to)));
    }

    @GetMapping("/returns")
    public ResponseEntity<ApiResponse<ReturnReportResponse>> getReturns(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(returnReportService.getReport(from, to)));
    }

    @GetMapping("/products")
    public ResponseEntity<ApiResponse<ProductReportResponse>> getProducts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(productReportService.getReport(from, to)));
    }
}
