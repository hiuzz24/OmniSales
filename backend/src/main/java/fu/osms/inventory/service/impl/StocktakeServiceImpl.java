package fu.osms.inventory.service.impl;

import fu.osms.auth.entity.User;
import fu.osms.auth.repository.UserRepository;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.common.enums.PlatformType;
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
import fu.osms.inventory.service.InventoryAlertService;
import fu.osms.inventory.service.StocktakeService;
import fu.osms.auth.repository.UserRoleRepository;
import fu.osms.notification.service.NotificationService;
import fu.osms.sync.service.MarketplaceInventoryPropagationService;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
    private final UserRoleRepository userRoleRepository;
    private final StocktakeMapper mapper;
    private final InventoryAlertService inventoryAlertService;
    private final NotificationService notificationService;
    private final MarketplaceInventoryPropagationService marketplaceInventoryPropagationService;
    private final ChannelProductVariantRepository channelProductVariantRepository;

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
                .notes(request.getNotes())
                .createdBy(user)
                .build();
        session = sessionRepository.save(session);
        replaceItems(session, request.getItems());

        if (complete) {
            session.setCompletedBy(user);
            session.setCompletedAt(OffsetDateTime.now());
            sessionRepository.save(session);
            applyStocktakeAdjustments(session, user);
            notifyStocktakeStatusChange(session, "DRAFT", "COMPLETED");
        } else {
            notifyStocktakeStatusChange(session, null, "DRAFT");
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
        session.setNotes(request.getNotes());
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
        String oldStatus = session.getStatus();
        if ("COMPLETED".equals(nextStatus)) {
            applyStocktakeAdjustments(session, user);
            session.setCompletedBy(user);
            session.setCompletedAt(OffsetDateTime.now());
        } else if ("IN_PROGRESS".equals(nextStatus)) {
            session.setStartedBy(user);
            session.setStartedAt(OffsetDateTime.now());
        } else if ("CANCELLED".equals(nextStatus)) {
            session.setCancelledBy(user);
            session.setCancelledAt(OffsetDateTime.now());
        }
        session.setStatus(nextStatus);
        StocktakeSession savedSession = sessionRepository.save(session);
        notifyStocktakeStatusChange(savedSession, oldStatus, nextStatus);
        return toResponse(savedSession);
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

    @Override
    @Transactional
    public int syncPendingMarketplaceInventory() {
        List<UUID> variantIds = itemRepository.findCompletedVariantIdsPendingMarketplaceSync();
        if (variantIds.isEmpty()) {
            return 0;
        }
        marketplaceInventoryPropagationService.pushAvailableStock(variantIds);
        return variantIds.size();
    }

    @Override
    @Transactional
    public int syncStocktakeMarketplaceInventory(UUID sessionId) {
        StocktakeSession session = findSession(sessionId);
        if (!"COMPLETED".equals(session.getStatus())) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Chỉ phiếu kiểm đã hoàn thành mới được đồng bộ lên sàn.");
        }
        List<UUID> variantIds = itemRepository.findCompletedVariantIdsBySessionId(sessionId);
        if (variantIds.isEmpty()) {
            return 0;
        }
        marketplaceInventoryPropagationService.pushAvailableStock(variantIds);
        return variantIds.size();
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
                    .difference(request.getActualQuantity() == null
                            ? null
                            : request.getActualQuantity() - request.getSystemQuantity())
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
            inventoryAlertService.notifyLowStockAfterStockChange(inventoryItem);

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
        int totalSystem = 0;
        int totalActual = 0;
        int totalDiff = 0;
        BigDecimal totalDiffValue = BigDecimal.ZERO;
        int checked = 0;
        int matched = 0;
        int surplus = 0;
        int shortage = 0;
        for (StocktakeItemResponse item : items) {
            totalSystem += item.getSystemQuantity() == null ? 0 : item.getSystemQuantity();
            boolean isChecked = item.getActualQuantity() != null;
            item.setChecked(isChecked);
            Integer diff = isChecked
                    ? item.getActualQuantity() - item.getSystemQuantity()
                    : null;
            item.setDifference(diff);
            if (!isChecked) {
                continue;
            }
            checked++;
            totalActual += item.getActualQuantity();
            totalDiff += diff;
            BigDecimal cost = item.getCostPrice() == null ? BigDecimal.ZERO : item.getCostPrice();
            BigDecimal diffValue = BigDecimal.valueOf(diff).multiply(cost);
            item.setDifferenceValue(diffValue);
            totalDiffValue = totalDiffValue.add(diffValue);
            if (diff == 0) {
                matched++;
            } else if (diff > 0) {
                surplus++;
            } else {
                shortage++;
            }
        }
        response.setItems(items);
        response.setTotalItems(items.size());
        response.setCheckedCount(checked);
        response.setMatchedCount(matched);
        response.setSurplusCount(surplus);
        response.setShortageCount(shortage);
        response.setTotalSystemQuantity(totalSystem);
        response.setTotalActualQuantity(totalActual);
        response.setTotalDifference(totalDiff);
        response.setTotalDifferenceValue(totalDiffValue);
        enrichMarketplaceInfo(response);
        return response;
    }

    private void enrichMarketplaceInfo(StocktakeSessionResponse response) {
        response.setMarketplaceSyncAvailable(false);
        response.setMarketplacePlatforms(List.of());
        if (response.getId() == null || !"COMPLETED".equals(response.getStatus())) {
            return;
        }
        List<UUID> variantIds = itemRepository.findCompletedVariantIdsBySessionId(response.getId());
        if (variantIds.isEmpty()) {
            return;
        }
        List<String> platforms = channelProductVariantRepository
                .findActiveByVariantIdInWithChannel(variantIds).stream()
                .map(ChannelProductVariant::getChannelProduct)
                .filter(java.util.Objects::nonNull)
                .map(item -> item.getChannel())
                .filter(java.util.Objects::nonNull)
                .map(item -> item.getPlatform())
                .filter(platform -> platform == PlatformType.SHOPIFY
                        || platform == PlatformType.LAZADA || platform == PlatformType.TIKTOK)
                .map(Enum::name).distinct().toList();
        response.setMarketplacePlatforms(platforms);
        boolean hasPendingSync = !platforms.isEmpty()
                && itemRepository.countPendingMarketplaceSyncVariantsBySessionId(response.getId()) > 0;
        response.setMarketplaceSyncAvailable(hasPendingSync);
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

    private void notifyStocktakeStatusChange(StocktakeSession session, String oldStatus, String newStatus) {
        try {
            if ("IN_PROGRESS".equals(newStatus) || ("DRAFT".equals(newStatus) && oldStatus == null)) {
                List<User> staffList = userRepository.findByWarehouseId(session.getWarehouse().getId());
                if (staffList != null) {
                    String title = "Yêu cầu kiểm kho mới";
                    String body = "Phiếu kiểm kho " + session.getSessionCode() + " đã được tạo tại kho " + session.getWarehouse().getName() + ". Vui lòng thực hiện kiểm kho.";
                    for (User staff : staffList) {
                        notificationService.createNotification(
                            staff.getId(),
                            "STOCKTAKE",
                            title,
                            body,
                            "INVENTORY",
                            session.getId()
                        );
                    }
                }
            } else if ("COMPLETED".equals(newStatus)) {
                List<StocktakeItem> items = itemRepository.findBySession_Id(session.getId());
                long diffCount = items.stream()
                        .filter(item -> item.getActualQuantity() != null && !item.getActualQuantity().equals(item.getSystemQuantity()))
                        .count();

                String title = "Hoàn tất kiểm kho";
                String body = "Phiếu kiểm kho " + session.getSessionCode() + " tại " + session.getWarehouse().getName() + " đã hoàn tất. " +
                        (diffCount > 0 ? "Phát hiện " + diffCount + " mặt hàng có chênh lệch tồn kho." : "Không có chênh lệch tồn kho.");

                userRoleRepository.findByRoleNameIn(List.of("OWNER", "OPERATIONS")).stream()
                        .map(userRole -> userRole.getUser().getId())
                        .distinct()
                        .forEach(userId -> {
                            try {
                                notificationService.createNotification(
                                        userId,
                                        "STOCKTAKE",
                                        title,
                                        body,
                                        "INVENTORY",
                                        session.getId()
                                );
                            } catch (Exception ex) {
                                System.err.println("Lỗi gửi thông báo kiểm kho cho quản lý: " + ex.getMessage());
                            }
                        });
            }
        } catch (Exception e) {
            System.err.println("Lỗi xử lý gửi thông báo kiểm kho: " + e.getMessage());
        }
    }
}
