package fu.osms.catalog.service.impl;

import fu.osms.catalog.dto.response.ProductLogResponse;
import fu.osms.catalog.entity.ProductLog;
import fu.osms.catalog.mapper.ProductLogMapper;
import fu.osms.catalog.repository.ProductLogRepository;
import fu.osms.catalog.service.ProductLogService;
import fu.osms.common.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductLogServiceImpl implements ProductLogService {

    private final ProductLogRepository productLogRepository;
    private final ProductLogMapper productLogMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductLogResponse> getLogs(UUID productId, int page, int size, String sortParam) {
        String[] sortParts = sortParam != null ? sortParam.split(",") : new String[]{"performedAt", "desc"};
        String sortField = sortParts[0];
        Sort.Direction direction = sortParts.length > 1 && sortParts[1].equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));
        
        Page<ProductLog> logPage;
        if (productId != null) {
            logPage = productLogRepository.findByProductIdOrderByPerformedAtDesc(productId, pageable);
        } else {
            logPage = productLogRepository.findAll(pageable);
        }

        List<ProductLogResponse> content = logPage.getContent().stream()
                .map(productLogMapper::toResponse)
                .toList();

        return PageResponse.<ProductLogResponse>builder()
                .content(content)
                .page(page)
                .size(size)
                .totalElements(logPage.getTotalElements())
                .totalPages(logPage.getTotalPages())
                .first(logPage.isFirst())
                .last(logPage.isLast())
                .build();
    }
}
