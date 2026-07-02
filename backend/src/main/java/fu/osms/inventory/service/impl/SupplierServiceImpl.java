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
import fu.osms.inventory.dto.request.SupplierRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    private String generateSupplierCode() {

        Optional<Supplier> latestSupplier =
                supplierRepository.findTopByOrderByCodeDesc();

        if (latestSupplier.isEmpty()) {
            return "NCC0001";
        }

        String latestCode = latestSupplier.get().getCode();

        try {
            int number = Integer.parseInt(
                    latestCode.replace("NCC", "")
            );

            return String.format("NCC%04d", number + 1);

        } catch (Exception e) {
            return "NCC0001";
        }
    }

    @Override
    @Transactional
    public SupplierResponse create(SupplierRequest request) {

        if (supplierRepository.existsByNameIgnoreCase(request.getName())) {
            throw new RuntimeException("Supplier already exists");
        }

        Supplier supplier = supplierMapper.toEntity(request);

        supplier.setCode(generateSupplierCode());
        supplier.setIsActive(true);

        Supplier savedSupplier = supplierRepository.save(supplier);

        return supplierMapper.toResponse(savedSupplier);
    }

    @Override
    @Transactional
    public SupplierResponse update(UUID id, SupplierRequest request) {

        Supplier supplier = supplierRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new RuntimeException("Supplier not found"));

        if (supplierRepository.existsByNameIgnoreCaseAndIdNot(request.getName(), id)) {
            throw new RuntimeException("Supplier name already exists.");
        }

        supplierMapper.updateEntityFromRequest(request, supplier);

        Supplier updatedSupplier = supplierRepository.save(supplier);

        return supplierMapper.toResponse(updatedSupplier);
    }

    @Override
    @Transactional
    public SupplierResponse updateStatus(UUID id, Boolean isActive) {

        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Supplier not found"));

        supplier.setIsActive(isActive);

        return supplierMapper.toResponse(
                supplierRepository.save(supplier)
        );
    }
}
