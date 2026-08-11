package fu.osms.reporting.service;

import fu.osms.reporting.dto.ReturnReportResponse;

import java.time.LocalDate;

public interface ReturnReportService {
    ReturnReportResponse getReport(LocalDate from, LocalDate to);
}
