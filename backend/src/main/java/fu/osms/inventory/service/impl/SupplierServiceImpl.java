package fu.osms.inventory.service.impl;

import fu.osms.common.dto.PageResponse;
import fu.osms.inventory.dto.response.SupplierResponse;
import fu.osms.inventory.entity.Supplier;
import fu.osms.inventory.mapper.SupplierMapper;
import fu.osms.inventory.repository.SupplierRepository;
import fu.osms.inventory.service.SupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SupplierServiceImpl implements SupplierService {

    private final SupplierRepository supplierRepository;
    private final SupplierMapper supplierMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SupplierResponse> getAll(int page, int size) {
        Page<Supplier> suppliersPage = supplierRepository
                .findByIsActiveTrueOrderByNameAsc(PageRequest.of(page, size));

        List<SupplierResponse> content = suppliersPage.getContent().stream()
                .map(supplierMapper::toResponse)
                .toList();

        return PageResponse.<SupplierResponse>builder()
                .content(content)
                .page(suppliersPage.getNumber())
                .size(suppliersPage.getSize())
                .totalElements(suppliersPage.getTotalElements())
                .totalPages(suppliersPage.getTotalPages())
                .first(suppliersPage.isFirst())
                .last(suppliersPage.isLast())
                .build();
    }
}
