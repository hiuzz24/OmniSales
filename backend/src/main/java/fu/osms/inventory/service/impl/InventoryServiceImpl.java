package fu.osms.inventory.service.impl;

import fu.osms.catalog.entity.Category;
import fu.osms.catalog.repository.CategoryRepository;
import fu.osms.auth.entity.User;
import fu.osms.auth.repository.UserRepository;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.common.dto.PageResponse;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.inventory.dto.request.InventoryItemRequest;
import fu.osms.inventory.dto.request.InventoryItemUpdateRequest;
import fu.osms.inventory.dto.request.InventoryTransactionRequest;
import fu.osms.inventory.dto.response.InventoryDetailDTO;
import fu.osms.inventory.dto.response.InventoryItemResponse;
import fu.osms.inventory.dto.response.InventoryTransactionResponse;
import fu.osms.inventory.dto.response.StockSummaryDTO;
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.inventory.entity.InventoryTransaction;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.inventory.enums.InvTxnType;
import fu.osms.inventory.mapper.AvailableVariantDTOMapper;
import fu.osms.inventory.mapper.InventoryDetailMapper;
import fu.osms.inventory.mapper.InventoryItemMapper;
import fu.osms.inventory.mapper.InventoryTransactionMapper;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.repository.InventoryTransactionRepository;
import fu.osms.inventory.repository.WarehouseRepository;
import fu.osms.inventory.service.InventoryService;
import fu.osms.inventory.service.InventoryAlertService;
import fu.osms.inventory.dto.response.AvailableVariantDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductVariantRepository variantRepository;
    private final UserRepository userRepository;
    private final InventoryItemMapper inventoryItemMapper;
    private final CategoryRepository categoryRepository;
    private final InventoryDetailMapper inventoryDetailMapper;
    private final InventoryTransactionMapper transactionMapper;
    private final AvailableVariantDTOMapper availableVariantDTOMapper;
    private final InventoryAlertService inventoryAlertService;
    private final ChannelProductVariantRepository channelProductVariantRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InventoryItemResponse> getAllInventoryItems(PageRequest pageRequest, int page, int size, UUID channelId, boolean localOnly, String keyword, String status, UUID warehouseId) {
        Page<InventoryItem> inventoryItemPage = inventoryItemRepository
                .findAllWithVariantRelationshipsFiltered(
                        channelId,
                        localOnly,
                        normalizeSearch(keyword),
                        normalizeStatus(status),
                        warehouseId,
                        pageRequest);

        List<InventoryItemResponse> dtoList = inventoryItemMapper.toResponseList(inventoryItemPage.getContent());
        enrichChannelInfo(dtoList, channelId);

        return PageResponse.<InventoryItemResponse>builder()
                .content(dtoList)
                .page(inventoryItemPage.getNumber())
                .size(inventoryItemPage.getSize())
                .totalElements(inventoryItemPage.getTotalElements())
                .totalPages(inventoryItemPage.getTotalPages())
                .first(inventoryItemPage.isFirst())
                .last(inventoryItemPage.isLast())
                .build();
    }

    @Override
    @Transactional
    public InventoryItemResponse createItem(InventoryItemRequest request) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryItemResponse getItemById(UUID id) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InventoryItemResponse> getItems(UUID warehouseId, int page, int size) {
        if (!warehouseRepository.existsById(warehouseId)) {
            throw new AppException(ErrorCode.WAREHOUSE_NOT_FOUND);
        }

        Page<InventoryItem> items = inventoryItemRepository.findByWarehouseId(warehouseId, PageRequest.of(page, size));
        List<InventoryItemResponse> content = items.getContent().stream()
                .map(inventoryItemMapper::toResponse)
                .toList();
        enrichChannelInfo(content, null);

        return PageResponse.<InventoryItemResponse>builder()
                .content(content)
                .page(items.getNumber())
                .size(items.getSize())
                .totalElements(items.getTotalElements())
                .totalPages(items.getTotalPages())
                .first(items.isFirst())
                .last(items.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public InventoryItemResponse getItemByWarehouseAndVariant(UUID warehouseId, UUID variantId) {
        InventoryItem item = inventoryItemRepository.findByWarehouseIdAndVariantId(warehouseId, variantId)
                .orElseThrow(() -> new AppException(ErrorCode.INVENTORY_ITEM_NOT_FOUND));
        InventoryItemResponse response = inventoryItemMapper.toResponse(item);
        enrichChannelInfo(List.of(response), null);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryItemResponse> getLowStockItems() {
        List<InventoryItemResponse> responses = inventoryItemMapper.toResponseList(inventoryItemRepository.findLowStockItems());
        enrichChannelInfo(responses, null);
        return responses;
    }

    @Override
    @Transactional
    public InventoryTransactionResponse recordTransaction(InventoryTransactionRequest request) {
        if (request.getQuantityChange() == null || request.getQuantityChange() == 0) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Adjustment quantity must be greater than 0");
        }
        if (!warehouseRepository.existsById(request.getWarehouseId())) {
            throw new AppException(ErrorCode.WAREHOUSE_NOT_FOUND);
        }
        ProductVariant variant = variantRepository.findById(request.getVariantId())
                .orElseThrow(() -> new AppException(ErrorCode.VARIANT_NOT_FOUND));

        InventoryItem item = inventoryItemRepository
                .findByWarehouseIdAndVariantIdWithLock(request.getWarehouseId(), request.getVariantId())
                .orElseThrow(() -> new AppException(ErrorCode.INVENTORY_ITEM_NOT_FOUND,
                        "Product is not available in selected warehouse."));

        int quantityBefore = item.getQuantityOnHand() != null ? item.getQuantityOnHand() : 0;
        int quantityAfter = quantityBefore + request.getQuantityChange();
        if (quantityAfter < 0) {
            throw new AppException(ErrorCode.NEGATIVE_STOCK_NOT_ALLOWED,
                    "Adjustment would result in negative inventory. Please reduce quantity.");
        }

        User currentUser = getCurrentUser();
        item.setQuantityOnHand(quantityAfter);
        item.setUpdatedBy(currentUser);
        inventoryItemRepository.save(item);
        inventoryAlertService.notifyLowStockAfterStockChange(item);

        InventoryTransaction transaction = InventoryTransaction.builder()
                .warehouse(item.getWarehouse())
                .variant(variant)
                .type(request.getType())
                .referenceType(request.getReferenceType())
                .referenceId(request.getReferenceId())
                .quantityChange(request.getQuantityChange())
                .quantityBefore(quantityBefore)
                .quantityAfter(quantityAfter)
                .unitCost(item.getAverageCost())
                .note(request.getNote())
                .performedBy(currentUser)
                .performedAt(OffsetDateTime.now())
                .build();
        return transactionMapper.toResponse(transactionRepository.save(transaction));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InventoryTransactionResponse> getTransactions(int page, int size) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InventoryTransactionResponse> getTransactionsByVariant(UUID variantId, int page, int size) {
        throw new UnsupportedOperationException("Not implemented");
    }

    private PageResponse<InventoryTransactionResponse> toTxnPageResponse(Page<InventoryTransaction> p, int page, int size) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, StockSummaryDTO> getStockSummary(Collection<UUID> variantIds) {
        if (variantIds == null || variantIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<InventoryItem> items = inventoryItemRepository.findByVariantIdIn(variantIds);

        return items.stream()
                .collect(Collectors.groupingBy(
                        item -> item.getVariant().getId(),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> {
                                    int totalAvailable = list.stream().mapToInt(i -> i.getAvailableQuantity() != null ? i.getAvailableQuantity() : 0).sum();
                                    int totalOnHand = list.stream().mapToInt(i -> i.getQuantityOnHand() != null ? i.getQuantityOnHand() : 0).sum();
                                    int totalReserved = list.stream().mapToInt(i -> i.getReservedQuantity() != null ? i.getReservedQuantity() : 0).sum();
                                    return StockSummaryDTO.builder()
                                            .availableQuantity(totalAvailable)
                                            .quantityOnHand(totalOnHand)
                                            .reservedQuantity(totalReserved)
                                            .build();
                                }
                        )
                ));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InventoryItemResponse> getInventoryByCategoryId(UUID categoryId, PageRequest pageRequest, int page, int size, UUID channelId, boolean localOnly, String keyword, String status, UUID warehouseId) {
        List<UUID> allCategoryIds = new ArrayList<>();

        findAllChildIds(categoryId, allCategoryIds);
        Page<InventoryItem> inventoryPage = inventoryItemRepository.findByCategoryIdInFiltered(
                allCategoryIds,
                channelId,
                localOnly,
                normalizeSearch(keyword),
                normalizeStatus(status),
                warehouseId,
                pageRequest);

        List<InventoryItemResponse> content = inventoryItemMapper.toResponseList(inventoryPage.getContent());
        enrichChannelInfo(content, channelId);

        return PageResponse.<InventoryItemResponse>builder()
                .content(content)
                .page(inventoryPage.getNumber())
                .size(inventoryPage.getSize())
                .totalElements(inventoryPage.getTotalElements())
                .totalPages(inventoryPage.getTotalPages())
                .first(inventoryPage.isFirst())
                .last(inventoryPage.isLast())
                .build();
    }

    private void enrichChannelInfo(List<InventoryItemResponse> responses, UUID preferredChannelId) {
        if (responses == null || responses.isEmpty()) {
            return;
        }

        List<UUID> variantIds = responses.stream()
                .map(InventoryItemResponse::getVariantId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (variantIds.isEmpty()) {
            return;
        }

        Map<UUID, PlatformType> platformByWarehouseId = resolveWarehousePlatforms(responses);
        List<ChannelProductVariant> mappings = preferredChannelId == null
                ? channelProductVariantRepository.findActiveByVariantIdInWithChannel(variantIds)
                : channelProductVariantRepository.findActiveByChannelIdAndVariantIdInWithVariant(preferredChannelId, variantIds);

        Map<UUID, Map<PlatformType, ChannelProductVariant>> mappingByVariantAndPlatform = new HashMap<>();
        Map<UUID, ChannelProductVariant> fallbackMappingByVariantId = new HashMap<>();
        for (ChannelProductVariant mapping : mappings) {
            if (mapping.getVariant() == null || mapping.getVariant().getId() == null
                    || mapping.getChannelProduct() == null || mapping.getChannelProduct().getChannel() == null) {
                continue;
            }
            UUID variantId = mapping.getVariant().getId();
            PlatformType platform = mapping.getChannelProduct().getChannel().getPlatform();
            mappingByVariantAndPlatform
                    .computeIfAbsent(variantId, ignored -> new EnumMap<>(PlatformType.class))
                    .putIfAbsent(platform, mapping);
            fallbackMappingByVariantId.putIfAbsent(variantId, mapping);
        }

        for (InventoryItemResponse response : responses) {
            PlatformType warehousePlatform = platformByWarehouseId.get(response.getWarehouseId());
            ChannelProductVariant mapping = warehousePlatform == null
                    ? null
                    : Optional.ofNullable(mappingByVariantAndPlatform.get(response.getVariantId()))
                    .map(byPlatform -> byPlatform.get(warehousePlatform))
                    .orElse(null);
            if (mapping == null) {
                mapping = fallbackMappingByVariantId.get(response.getVariantId());
            }
            if (mapping == null || mapping.getChannelProduct() == null || mapping.getChannelProduct().getChannel() == null) {
                continue;
            }
            response.setChannelId(mapping.getChannelProduct().getChannel().getId());
            response.setChannelName(mapping.getChannelProduct().getChannel().getDisplayName());
            response.setPlatform(mapping.getChannelProduct().getChannel().getPlatform());
        }
    }

    private String normalizeSearch(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) {
            return null;
        }
        return status.trim().toLowerCase(Locale.ROOT);
    }

    private Map<UUID, PlatformType> resolveWarehousePlatforms(List<InventoryItemResponse> responses) {
        List<UUID> warehouseIds = responses.stream()
                .map(InventoryItemResponse::getWarehouseId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (warehouseIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, PlatformType> result = new HashMap<>();
        for (Warehouse warehouse : warehouseRepository.findAllById(warehouseIds)) {
            PlatformType platform = resolveMarketplacePlatform(warehouse);
            if (platform != null) {
                result.put(warehouse.getId(), platform);
            }
        }
        return result;
    }

    @Override
    public void findAllChildIds(UUID parentId, List<UUID> resultList) {
        List<Category> children = categoryRepository.findByParentId(parentId);
        resultList.add(parentId);
        for (Category child : children) {
            resultList.add(child.getId());
            findAllChildIds(child.getId(), resultList);
        }

    }

    @Override
    @Transactional
    public InventoryDetailDTO getInventoryItemDetail(UUID inventoryItemId) {
        InventoryItem inventoryItem = inventoryItemRepository.findDetailById(inventoryItemId)
                .orElseThrow(() -> new AppException(ErrorCode.INVENTORY_ITEM_NOT_FOUND,
                        "Không tìm thấy thông tin tồn kho yêu cầu"));
        InventoryDetailDTO dto = inventoryDetailMapper.toDetailDTO(inventoryItem);

        UUID variantId = inventoryItem.getVariant().getId();
        PlatformType warehousePlatform = resolveMarketplacePlatform(inventoryItem.getWarehouse());

        channelProductVariantRepository.findActiveByVariantIdWithChannel(variantId).stream()
                .filter(mapping -> warehousePlatform == null
                        || mapping.getChannelProduct().getChannel().getPlatform() == warehousePlatform)
                .findFirst()
                .ifPresent(mapping -> {
                    dto.setChannelId(mapping.getChannelProduct().getChannel().getId());
                    dto.setPlatform(mapping.getChannelProduct().getChannel().getPlatform());
                });

        dto.setLastUpdatedAt(inventoryItem.getUpdatedAt());

        transactionRepository.findFirstByVariantIdOrderByPerformedAtDesc(variantId)
                .ifPresent(txn -> {
                    if (dto.getLastUpdatedAt() == null || txn.getPerformedAt().isAfter(dto.getLastUpdatedAt())) {
                        dto.setLastUpdatedAt(txn.getPerformedAt());
                    }
                });

        transactionRepository.findFirstByVariantIdAndTypeOrderByPerformedAtDesc(variantId, InvTxnType.IMPORT)
                .ifPresent(txn -> dto.setLastImportedAt(txn.getPerformedAt()));

        return dto;
    }

    @Override
    @Transactional
    public InventoryDetailDTO updateInventoryDetail(UUID inventoryItemId, InventoryItemUpdateRequest request) {
        InventoryItem inventoryItem = inventoryItemRepository.findById(inventoryItemId)
                .orElseThrow(() -> new AppException(ErrorCode.INVENTORY_ITEM_NOT_FOUND,
                        "Không tìm thấy thông tin tồn kho yêu cầu"));

        // If warehouse is changed, check constraint
        if (request.getWarehouseId() != null && !request.getWarehouseId().equals(inventoryItem.getWarehouse().getId())) {
            Optional<InventoryItem> existing = inventoryItemRepository.findByWarehouseIdAndVariantId(request.getWarehouseId(), inventoryItem.getVariant().getId());
            if (existing.isPresent() && !existing.get().getId().equals(inventoryItemId)) {
                throw new IllegalArgumentException("Sản phẩm này đã tồn tại trong kho hàng được chọn.");
            }
            fu.osms.inventory.entity.Warehouse warehouse = warehouseRepository.findById(request.getWarehouseId())
                    .orElseThrow(() -> new IllegalArgumentException("Kho hàng không tồn tại."));
            inventoryItem.setWarehouse(warehouse);
        }

        if (request.getQuantityOnHand() != null) {
            inventoryItem.setQuantityOnHand(request.getQuantityOnHand());
        }
        if (request.getAverageCost() != null) {
            inventoryItem.setAverageCost(request.getAverageCost());
        }

        ProductVariant variant = inventoryItem.getVariant();
        if (variant != null) {
            if (request.getProductVariantName() != null) {
                variant.setName(request.getProductVariantName());
            }
            if (request.getPrice() != null) {
                variant.setPrice(request.getPrice());
            }
            if (request.getAverageCost() != null) {
                variant.setCostPrice(request.getAverageCost());
            }
            variantRepository.save(variant);
        }

        User currentUser = getCurrentUser();
        inventoryItem.setUpdatedBy(currentUser);
        inventoryItem = inventoryItemRepository.save(inventoryItem);
        inventoryAlertService.notifyLowStockAfterStockChange(inventoryItem);

        // Fetch refreshed details to return
        return getInventoryItemDetail(inventoryItemId);
    }

    @Override
    public List<AvailableVariantDTO> getAvailableVariantsByWarehouse(UUID warehouseId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new AppException(ErrorCode.WAREHOUSE_NOT_FOUND));

        PlatformType warehousePlatform = resolveMarketplacePlatform(warehouse);
        if (warehousePlatform != null) {
            return getMarketplaceVariantsForWarehouse(warehouse, warehousePlatform);
        }

        List<InventoryItem> inventoryItems = warehousePlatform == null
                ? inventoryItemRepository.findByWarehouseId(warehouseId)
                : inventoryItemRepository.findByWarehouseIdAndMappedPlatform(warehouseId, warehousePlatform);

        List<AvailableVariantDTO> variants = inventoryItems.stream()
                .map(item -> availableVariantDTOMapper.toAvailableDto(item.getVariant(), item))
                .collect(Collectors.toList());
        enrichAvailableVariantChannelInfo(variants, warehousePlatform);
        return variants;
    }

    private List<AvailableVariantDTO> getMarketplaceVariantsForWarehouse(Warehouse warehouse, PlatformType platform) {
        List<ChannelProductVariant> mappings = channelProductVariantRepository.findActiveByPlatformWithVariant(platform);
        if (mappings.isEmpty()) {
            return List.of();
        }

        Map<UUID, ChannelProductVariant> mappingByVariantId = new LinkedHashMap<>();
        for (ChannelProductVariant mapping : mappings) {
            if (mapping.getVariant() == null || mapping.getVariant().getId() == null) {
                continue;
            }
            mappingByVariantId.putIfAbsent(mapping.getVariant().getId(), mapping);
        }
        if (mappingByVariantId.isEmpty()) {
            return List.of();
        }

        List<InventoryItem> existingItems = inventoryItemRepository.findByWarehouseIdAndVariantIdIn(
                warehouse.getId(),
                mappingByVariantId.keySet()
        );
        Map<UUID, InventoryItem> itemByVariantId = existingItems.stream()
                .filter(item -> item.getVariant() != null && item.getVariant().getId() != null)
                .collect(Collectors.toMap(
                        item -> item.getVariant().getId(),
                        item -> item,
                        (first, ignored) -> first
                ));

        return mappingByVariantId.values().stream()
                .map(mapping -> toMarketplaceAvailableVariant(mapping, itemByVariantId.get(mapping.getVariant().getId())))
                .toList();
    }

    private AvailableVariantDTO toMarketplaceAvailableVariant(ChannelProductVariant mapping, InventoryItem item) {
        ProductVariant variant = mapping.getVariant();
        BigDecimal averageCost = item != null && item.getAverageCost() != null
                ? item.getAverageCost()
                : variant.getCostPrice();
        Integer availableQuantity = item != null && item.getAvailableQuantity() != null
                ? item.getAvailableQuantity()
                : 0;

        return AvailableVariantDTO.builder()
                .variantId(variant.getId())
                .sku(variant.getSku())
                .productName(variant.getProduct() == null ? variant.getName() : variant.getProduct().getName())
                .variantName(variant.getName())
                .unitPrice(variant.getPrice())
                .averageCost(averageCost == null ? BigDecimal.ZERO : averageCost)
                .availableQuantity(availableQuantity)
                .channelId(mapping.getChannelProduct().getChannel().getId())
                .channelName(mapping.getChannelProduct().getChannel().getDisplayName())
                .platform(mapping.getChannelProduct().getChannel().getPlatform())
                .build();
    }

    private PlatformType resolveMarketplacePlatform(Warehouse warehouse) {
        String name = warehouse.getName() == null ? "" : warehouse.getName().toLowerCase(Locale.ROOT);
        String address = warehouse.getAddress() == null ? "" : warehouse.getAddress().toLowerCase(Locale.ROOT);

        if (address.contains("shopify_location_id=") || name.startsWith("shopify")) {
            return PlatformType.SHOPIFY;
        }
        if (address.contains("lazada_warehouse_code=") || name.startsWith("lazada")) {
            return PlatformType.LAZADA;
        }
        return null;
    }

    private void enrichAvailableVariantChannelInfo(List<AvailableVariantDTO> variants, PlatformType preferredPlatform) {
        if (variants == null || variants.isEmpty()) {
            return;
        }

        List<UUID> variantIds = variants.stream()
                .map(AvailableVariantDTO::getVariantId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (variantIds.isEmpty()) {
            return;
        }

        List<ChannelProductVariant> mappings = channelProductVariantRepository.findActiveByVariantIdInWithChannel(variantIds);
        Map<UUID, ChannelProductVariant> mappingByVariantId = new HashMap<>();
        for (ChannelProductVariant mapping : mappings) {
            if (mapping.getVariant() == null || mapping.getVariant().getId() == null
                    || mapping.getChannelProduct() == null || mapping.getChannelProduct().getChannel() == null) {
                continue;
            }
            if (preferredPlatform != null && mapping.getChannelProduct().getChannel().getPlatform() != preferredPlatform) {
                continue;
            }
            mappingByVariantId.putIfAbsent(mapping.getVariant().getId(), mapping);
        }

        for (AvailableVariantDTO variant : variants) {
            ChannelProductVariant mapping = mappingByVariantId.get(variant.getVariantId());
            if (mapping == null) {
                continue;
            }
            variant.setChannelId(mapping.getChannelProduct().getChannel().getId());
            variant.setChannelName(mapping.getChannelProduct().getChannel().getDisplayName());
            variant.setPlatform(mapping.getChannelProduct().getChannel().getPlatform());
        }
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        return userRepository.findByEmail(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }


}
