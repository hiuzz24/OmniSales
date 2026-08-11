package fu.osms.reporting.service;

import fu.osms.reporting.dto.ProductReportResponse;

import java.time.LocalDate;

public interface ProductReportService {
    ProductReportResponse getReport(LocalDate from, LocalDate to);
}
