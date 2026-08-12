package fu.osms.reporting.service;

import fu.osms.reporting.dto.ChannelOrderReportResponse;

import java.time.LocalDate;

public interface ChannelOrderReportService {
    ChannelOrderReportResponse getReport(LocalDate from, LocalDate to);
}
