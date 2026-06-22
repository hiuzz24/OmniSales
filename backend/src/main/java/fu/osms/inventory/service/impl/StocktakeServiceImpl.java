package fu.osms.inventory.service.impl;

import fu.osms.auth.entity.User;
import fu.osms.auth.repository.UserRepository;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.inventory.dto.request.StocktakeItemRequest;
import fu.osms.inventory.dto.request.StocktakeSessionRequest;
import fu.osms.inventory.dto.response.StocktakeItemResponse;
import fu.osms.inventory.dto.response.StocktakeSessionResponse;
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.inventory.entity.InventoryTransaction;
import fu.osms.inventory.entity.StocktakeItem;
import fu.osms.inventory.entity.StocktakeSession;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.inventory.enums.InvTxnType;
import fu.osms.inventory.mapper.StocktakeMapper;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.repository.InventoryTransactionRepository;
import fu.osms.inventory.repository.StocktakeItemRepository;
import fu.osms.inventory.repository.StocktakeSessionRepository;
import fu.osms.inventory.repository.WarehouseRepository;
import fu.osms.inventory.service.StocktakeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StocktakeServiceImpl implements StocktakeService {

    private final StocktakeSessionRepository sessionRepository;
    private final StocktakeItemRepository itemRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final StocktakeMapper mapper;

    @Override
    @Transactional
    public StocktakeSessionResponse createStocktake(StocktakeSessionRequest request, boolean complete) {
        Warehouse warehouse = getActiveWarehouse(request.getWarehouseId());
        User user = getCurrentUser();

        StocktakeSession session = StocktakeSession.builder()
                .warehouse(warehouse)
                .sessionCode(hasText(request.getSessionCode()) ? request.getSessionCode() : generateSessionCode())
                .scheduledDate(request.getScheduledDate())
                .status(complete ? "COMPLETED" : "DRAFT")
                .createdBy(user)
                .build();
        session = sessionRepository.save(session);
        replaceItems(session, request.getItems());

        if (complete) {
            applyStocktakeAdjustments(session, user);
        }
        return toResponse(session);
    }

    @Override
    @Transactional(readOnly = true)
    public StocktakeSessionResponse getStocktakeById(UUID id) {
        return toResponse(findSession(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StocktakeSessionResponse> getStocktakes(UUID warehouseId, String status, String keyword, Pageable pageable) {
        Specification<StocktakeSession> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (warehouseId != null) {
                predicates.add(cb.equal(root.get("warehouse").get("id"), warehouseId));
            }
            if (hasText(status)) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (hasText(keyword)) {
                String likeKeyword = "%" + keyword.trim().toLowerCase() + "%";
                var warehouseJoin = root.join("warehouse", JoinType.LEFT);
                var createdByJoin = root.join("createdBy", JoinType.LEFT);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("sessionCode")), likeKeyword),
                        cb.like(cb.lower(warehouseJoin.get("name")), likeKeyword),
                        cb.like(cb.lower(createdByJoin.get("fullName")), likeKeyword)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return sessionRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Override
    @Transactional
    public StocktakeSessionResponse updateStocktake(UUID id, StocktakeSessionRequest request) {
        StocktakeSession session = findSession(id);
        if ("COMPLETED".equals(session.getStatus()) || "CANCELLED".equals(session.getStatus())) {
            throw new AppException(ErrorCode.CONFLICT, "Completed or cancelled stocktake cannot be changed");
        }

        session.setWarehouse(getActiveWarehouse(request.getWarehouseId()));
        session.setScheduledDate(request.getScheduledDate());
        if (hasText(request.getSessionCode())) {
            session.setSessionCode(request.getSessionCode());
        }
        replaceItems(session, request.getItems());
        return toResponse(sessionRepository.save(session));
    }

    @Override
    @Transactional
    public StocktakeSessionResponse changeStatus(UUID id, String status) {
        StocktakeSession session = findSession(id);
        String nextStatus = normalizeStatus(status);

        if ("COMPLETED".equals(session.getStatus()) || "CANCELLED".equals(session.getStatus())) {
            throw new AppException(ErrorCode.CONFLICT, "Completed or cancelled stocktake cannot change status");
        }

        User user = getCurrentUser();
        if ("COMPLETED".equals(nextStatus)) {
            applyStocktakeAdjustments(session, user);
        }
        session.setStatus(nextStatus);
        return toResponse(sessionRepository.save(session));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCount", sessionRepository.count());
        stats.put("draftCount", sessionRepository.countByStatus("DRAFT"));
        stats.put("inProgressCount", sessionRepository.countByStatus("IN_PROGRESS"));
        stats.put("completedCount", sessionRepository.countByStatus("COMPLETED"));
        stats.put("cancelledCount", sessionRepository.countByStatus("CANCELLED"));
        return stats;
    }

    private void replaceItems(StocktakeSession session, List<StocktakeItemRequest> items) {
        itemRepository.deleteBySession_Id(session.getId());
        if (items == null || items.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Stocktake must include at least one item");
        }

        for (StocktakeItemRequest request : items) {
            ProductVariant variant = variantRepository.findById(request.getVariantId())
                    .orElseThrow(() -> new AppException(ErrorCode.VARIANT_NOT_FOUND));
            StocktakeItem item = StocktakeItem.builder()
                    .session(session)
                    .variant(variant)
                    .systemQuantity(request.getSystemQuantity())
                    .actualQuantity(request.getActualQuantity())
                    .notes(request.getNotes())
                    .build();
            itemRepository.save(item);
        }
    }

    private void applyStocktakeAdjustments(StocktakeSession session, User user) {
        List<StocktakeItem> items = itemRepository.findBySession_Id(session.getId());
        if (items.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Cannot complete stocktake without items");
        }

        for (StocktakeItem item : items) {
            if (item.getActualQuantity() == null || item.getActualQuantity() < 0) {
                throw new AppException(ErrorCode.VALIDATION_FAILED, "Actual quantity is required before completing stocktake");
            }
            int difference = item.getActualQuantity() - item.getSystemQuantity();
            if (difference == 0) {
                continue;
            }

            InventoryItem inventoryItem = inventoryItemRepository
                    .findByWarehouseIdAndVariantIdWithLock(session.getWarehouse().getId(), item.getVariant().getId())
                    .orElseThrow(() -> new AppException(ErrorCode.INVENTORY_ITEM_NOT_FOUND));
            int quantityBefore = inventoryItem.getQuantityOnHand() != null ? inventoryItem.getQuantityOnHand() : 0;
            int quantityAfter = quantityBefore + difference;
            if (quantityAfter < 0) {
                throw new AppException(ErrorCode.NEGATIVE_STOCK_NOT_ALLOWED, "Adjustment would result in negative inventory. Please reduce quantity.");
            }

            inventoryItem.setQuantityOnHand(quantityAfter);
            inventoryItem.setUpdatedBy(user);
            inventoryItemRepository.save(inventoryItem);

            InventoryTransaction transaction = InventoryTransaction.builder()
                    .warehouse(session.getWarehouse())
                    .variant(item.getVariant())
                    .type(InvTxnType.ADJUSTMENT)
                    .referenceType("ADJUSTMENT")
                    .referenceId(session.getId())
                    .quantityChange(difference)
                    .quantityBefore(quantityBefore)
                    .quantityAfter(quantityAfter)
                    .unitCost(inventoryItem.getAverageCost())
                    .performedBy(user)
                    .note("Stock check adjustment: " + session.getSessionCode())
                    .build();
            transactionRepository.save(transaction);
        }
    }

    private StocktakeSessionResponse toResponse(StocktakeSession session) {
        StocktakeSessionResponse response = mapper.toResponse(session);
        List<StocktakeItemResponse> items = itemRepository.findBySession_Id(session.getId()).stream()
                .map(mapper::toItemResponse)
                .toList();
        response.setItems(items);
        return response;
    }

    private StocktakeSession findSession(UUID id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.STOCKTAKE_NOT_FOUND));
    }

    private Warehouse getActiveWarehouse(UUID warehouseId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new AppException(ErrorCode.WAREHOUSE_NOT_FOUND));
        if (!Boolean.TRUE.equals(warehouse.getIsActive())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Warehouse must be active");
        }
        return warehouse;
    }

    private String normalizeStatus(String status) {
        return switch (status) {
            case "DRAFT", "IN_PROGRESS", "COMPLETED", "CANCELLED" -> status;
            default -> throw new AppException(ErrorCode.VALIDATION_FAILED, "Invalid stocktake status");
        };
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        return userRepository.findByEmail(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private String generateSessionCode() {
        int year = LocalDate.now().getYear();
        long count = sessionRepository.countByCreatedYear(year);
        return "KK-" + year + "-" + String.format("%03d", count + 1);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
