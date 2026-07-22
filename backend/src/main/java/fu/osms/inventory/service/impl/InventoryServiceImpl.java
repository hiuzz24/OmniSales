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
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    private static final String SHARED_WAREHOUSE_NAME = "Kho mặc định đa sàn";

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
    @Transactional
    public PageResponse<InventoryItemResponse> getAllInventoryItems(PageRequest pageRequest, int page, int size, UUID channelId, boolean localOnly, String keyword, String status, UUID warehouseId, Collection<PlatformType> platforms) {
        ensureInventoryItemsForExistingVariants();
        Page<InventoryItem> inventoryItemPage = inventoryItemRepository
                .findAllWithVariantRelationshipsFiltered(
                        channelId,
                        localOnly,
                        normalizeSearch(keyword),
                        normalizeStatus(status),
                        warehouseId,
                        Pageable.unpaged());

        List<InventoryItemResponse> dtoList = aggregateInventoryItems(inventoryItemPage.getContent(), channelId);
        dtoList = filterByPlatforms(dtoList, platforms);
        long totalProducts = countParentProducts(dtoList);
        dtoList = sortInventoryResponses(dtoList, pageRequest.getSort());
        List<InventoryItemResponse> pageContent = paginate(dtoList, page, size);

        return PageResponse.<InventoryItemResponse>builder()
                .content(pageContent)
                .page(page)
                .size(size)
                .totalElements(dtoList.size())
                .totalProducts(totalProducts)
                .totalSkus((long) dtoList.size())
                .totalPages(totalPages(dtoList.size(), size))
                .first(page <= 0)
                .last(page >= totalPages(dtoList.size(), size) - 1)
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
        Page<InventoryItem> inventoryItems = inventoryItemRepository
                .findAllWithVariantRelationshipsFiltered(null, false, null, null, null, Pageable.unpaged());
        return aggregateInventoryItems(inventoryItems.getContent(), null).stream()
                .filter(this::isLowStockResponse)
                .sorted(Comparator
                        .comparing((InventoryItemResponse item) -> safeInt(item.getAvailableQuantity()))
                        .thenComparing(InventoryItemResponse::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
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
    @Transactional
    public PageResponse<InventoryItemResponse> getInventoryByCategoryId(UUID categoryId, PageRequest pageRequest, int page, int size, UUID channelId, boolean localOnly, String keyword, String status, UUID warehouseId, Collection<PlatformType> platforms) {
        ensureInventoryItemsForExistingVariants();
        List<UUID> allCategoryIds = new ArrayList<>();

        findAllChildIds(categoryId, allCategoryIds);
        Page<InventoryItem> inventoryPage = inventoryItemRepository.findByCategoryIdInFiltered(
                allCategoryIds,
                channelId,
                localOnly,
                normalizeSearch(keyword),
                normalizeStatus(status),
                warehouseId,
                Pageable.unpaged());

        List<InventoryItemResponse> content = aggregateInventoryItems(inventoryPage.getContent(), channelId);
        content = filterByPlatforms(content, platforms);
        long totalProducts = countParentProducts(content);
        content = sortInventoryResponses(content, pageRequest.getSort());
        List<InventoryItemResponse> pageContent = paginate(content, page, size);

        return PageResponse.<InventoryItemResponse>builder()
                .content(pageContent)
                .page(page)
                .size(size)
                .totalElements(content.size())
                .totalProducts(totalProducts)
                .totalSkus((long) content.size())
                .totalPages(totalPages(content.size(), size))
                .first(page <= 0)
                .last(page >= totalPages(content.size(), size) - 1)
                .build();
    }

    private List<InventoryItemResponse> aggregateInventoryItems(List<InventoryItem> inventoryItems, UUID preferredChannelId) {
        List<InventoryItemResponse> responses = inventoryItemMapper.toResponseList(inventoryItems);
        enrichChannelInfo(responses, preferredChannelId);
        if (responses.isEmpty()) {
            return responses;
        }

        Map<UUID, ChannelSummary> channelSummaryByVariantId = loadChannelSummaries(
                responses.stream()
                        .map(InventoryItemResponse::getVariantId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList(),
                preferredChannelId
        );

        Map<String, List<InventoryItemResponse>> bySku = responses.stream()
                .collect(Collectors.groupingBy(
                        item -> inventoryAggregateKey(item, channelSummaryByVariantId),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<InventoryItemResponse> result = new ArrayList<>();
        for (List<InventoryItemResponse> group : bySku.values()) {
            result.add(aggregateSkuGroup(group, channelSummaryByVariantId));
        }
        return result;
    }

    private InventoryItemResponse aggregateSkuGroup(List<InventoryItemResponse> group,
                                                    Map<UUID, ChannelSummary> channelSummaryByVariantId) {
        InventoryItemResponse first = group.get(0);
        InventoryItemResponse stockSource = group.stream()
                .max(Comparator
                        .comparingInt((InventoryItemResponse item) -> safeInt(item.getQuantityOnHand()))
                        .thenComparingInt(item -> safeInt(item.getAvailableQuantity())))
                .orElse(first);
        int quantityOnHand = safeInt(stockSource.getQuantityOnHand());
        int reservedQuantity = safeInt(stockSource.getReservedQuantity());
        int availableQuantity = safeInt(stockSource.getAvailableQuantity());
        int lowStockThreshold = group.stream().mapToInt(item -> safeInt(item.getLowStockThreshold())).max().orElse(0);

        List<UUID> variantIds = group.stream()
                .map(InventoryItemResponse::getVariantId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<UUID> productIds = group.stream()
                .map(InventoryItemResponse::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<UUID> channelIds = new ArrayList<>();
        List<String> channelNames = new ArrayList<>();
        List<PlatformType> platforms = new ArrayList<>();
        List<String> marketplaceSkus = new ArrayList<>();
        for (UUID variantId : variantIds) {
            ChannelSummary summary = channelSummaryByVariantId.get(variantId);
            if (summary == null) {
                continue;
            }
            summary.channelIds().forEach(id -> {
                if (!channelIds.contains(id)) {
                    channelIds.add(id);
                }
            });
            summary.channelNames().forEach(name -> {
                if (!channelNames.contains(name)) {
                    channelNames.add(name);
                }
            });
            summary.platforms().forEach(platform -> {
                if (!platforms.contains(platform)) {
                    platforms.add(platform);
                }
            });
            summary.externalSkus().forEach(sku -> {
                if (!marketplaceSkus.contains(sku)) {
                    marketplaceSkus.add(sku);
                }
            });
        }
        String marketplaceSku = marketplaceSkus.size() == 1 ? marketplaceSkus.get(0) : null;

        List<String> warehouseNames = group.stream()
                .map(InventoryItemResponse::getWarehouseName)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
        OffsetDateTime updatedAt = group.stream()
                .map(InventoryItemResponse::getUpdatedAt)
                .filter(Objects::nonNull)
                .max(OffsetDateTime::compareTo)
                .orElse(first.getUpdatedAt());

        return InventoryItemResponse.builder()
                .id(first.getId())
                .warehouseId(group.stream().map(InventoryItemResponse::getWarehouseId).distinct().count() == 1
                        ? first.getWarehouseId()
                        : null)
                .warehouseName(warehouseNames.size() <= 1
                        ? first.getWarehouseName()
                        : warehouseNames.size() + " kho")
                .productId(productIds.size() == 1 ? productIds.get(0) : null)
                .productIds(productIds)
                .variantId(first.getVariantId())
                .variantSku(firstNonBlank(marketplaceSku, first.getVariantSku()))
                .marketplaceSku(marketplaceSku)
                .productName(firstNonBlank(first.getProductName(), first.getVariantName()))
                .variantName(first.getVariantName())
                .channelId(channelIds.size() == 1 ? channelIds.get(0) : null)
                .channelName(channelNames.size() == 1 ? channelNames.get(0) : String.join(", ", channelNames))
                .platform(platforms.size() == 1 ? platforms.get(0) : null)
                .channelIds(channelIds)
                .channelNames(channelNames)
                .platforms(platforms)
                .mergedInventoryItemCount(group.size())
                .mergedVariantCount(variantIds.size())
                .quantityOnHand(quantityOnHand)
                .reservedQuantity(reservedQuantity)
                .availableQuantity(availableQuantity)
                .averageCost(first.getAverageCost())
                .lowStockThreshold(lowStockThreshold)
                .isLowStock(availableQuantity <= lowStockThreshold)
                .updatedAt(updatedAt)
                .build();
    }

    private Map<UUID, ChannelSummary> loadChannelSummaries(List<UUID> variantIds, UUID preferredChannelId) {
        if (variantIds == null || variantIds.isEmpty()) {
            return Map.of();
        }
        List<ChannelProductVariant> mappings = preferredChannelId == null
                ? channelProductVariantRepository.findActiveByVariantIdInWithChannel(variantIds)
                : channelProductVariantRepository.findActiveByChannelIdAndVariantIdInWithVariant(preferredChannelId, variantIds);

        Map<UUID, MutableChannelSummary> mutable = new HashMap<>();
        Map<String, Set<UUID>> variantIdsBySku = new LinkedHashMap<>();
        for (ChannelProductVariant mapping : mappings) {
            if (mapping.getVariant() == null || mapping.getVariant().getId() == null
                    || mapping.getChannelProduct() == null || mapping.getChannelProduct().getChannel() == null) {
                continue;
            }
            UUID variantId = mapping.getVariant().getId();
            addChannelMappingSummary(mutable.computeIfAbsent(variantId, ignored -> new MutableChannelSummary()), mapping);
            String skuKey = channelMappingSkuKey(mapping);
            if (!skuKey.isBlank()) {
                variantIdsBySku.computeIfAbsent(skuKey, ignored -> new LinkedHashSet<>()).add(variantId);
            }
        }

        if (preferredChannelId == null && !variantIdsBySku.isEmpty()) {
            List<ChannelProductVariant> skuGroupMappings = channelProductVariantRepository
                    .findActiveByNormalizedSkuInWithVariant(new ArrayList<>(variantIdsBySku.keySet()));
            for (ChannelProductVariant mapping : skuGroupMappings) {
                String skuKey = channelMappingSkuKey(mapping);
                Set<UUID> targetVariantIds = variantIdsBySku.get(skuKey);
                if (targetVariantIds == null || targetVariantIds.isEmpty()) {
                    continue;
                }
                for (UUID targetVariantId : targetVariantIds) {
                    addChannelMappingSummary(
                            mutable.computeIfAbsent(targetVariantId, ignored -> new MutableChannelSummary()),
                            mapping
                    );
                }
            }
        }

        Map<UUID, ChannelSummary> result = new HashMap<>();
        mutable.forEach((variantId, summary) -> result.put(variantId, new ChannelSummary(
                new ArrayList<>(summary.channelIds),
                new ArrayList<>(summary.channelNames),
                new ArrayList<>(summary.platforms),
                new ArrayList<>(summary.externalSkus)
        )));
        return result;
    }

    private void addChannelMappingSummary(MutableChannelSummary summary, ChannelProductVariant mapping) {
        if (summary == null || mapping == null || mapping.getChannelProduct() == null
                || mapping.getChannelProduct().getChannel() == null) {
            return;
        }
        var channel = mapping.getChannelProduct().getChannel();
        if (channel.getId() != null) {
            summary.channelIds.add(channel.getId());
        }
        if (channel.getDisplayName() != null && !channel.getDisplayName().isBlank()) {
            summary.channelNames.add(channel.getDisplayName());
        }
        if (channel.getPlatform() != null) {
            summary.platforms.add(channel.getPlatform());
        }
        String externalSku = normalizeSkuValue(mapping.getExternalSku());
        if (externalSku != null) {
            summary.externalSkus.add(externalSku);
        }
    }

    private String channelMappingSkuKey(ChannelProductVariant mapping) {
        if (mapping == null) {
            return "";
        }
        return normalizeSkuKey(firstNonBlank(
                mapping.getExternalSku(),
                mapping.getVariant() == null ? null : mapping.getVariant().getSku()
        ));
    }

    private List<InventoryItemResponse> sortInventoryResponses(List<InventoryItemResponse> source, Sort sort) {
        if (sort == null || sort.isUnsorted() || source.isEmpty()) {
            return source;
        }
        Sort.Order order = sort.iterator().next();
        Comparator<InventoryItemResponse> comparator = comparatorFor(order.getProperty());
        if (order.isDescending()) {
            comparator = comparator.reversed();
        }
        return source.stream().sorted(comparator).toList();
    }

    private List<InventoryItemResponse> filterByPlatforms(List<InventoryItemResponse> source, Collection<PlatformType> requiredPlatforms) {
        if (requiredPlatforms == null || requiredPlatforms.isEmpty() || source.isEmpty()) {
            return source;
        }
        Set<PlatformType> required = requiredPlatforms.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (required.isEmpty()) {
            return source;
        }
        return source.stream()
                .filter(item -> itemPlatforms(item).containsAll(required))
                .toList();
    }

    private Set<PlatformType> itemPlatforms(InventoryItemResponse item) {
        Set<PlatformType> platforms = new LinkedHashSet<>();
        if (item.getPlatforms() != null) {
            platforms.addAll(item.getPlatforms());
        }
        if (item.getPlatform() != null) {
            platforms.add(item.getPlatform());
        }
        return platforms;
    }

    private Comparator<InventoryItemResponse> comparatorFor(String property) {
        return switch (property == null ? "" : property) {
            case "quantityOnHand" -> Comparator.comparing(item -> safeInt(item.getQuantityOnHand()));
            case "availableQuantity" -> Comparator.comparing(item -> safeInt(item.getAvailableQuantity()));
            case "variantSku" -> Comparator.comparing(item -> safeText(item.getVariantSku()), String.CASE_INSENSITIVE_ORDER);
            case "variantName" -> Comparator.comparing(item -> safeText(item.getVariantName()), String.CASE_INSENSITIVE_ORDER);
            case "productName" -> Comparator.comparing(item -> safeText(item.getProductName()), String.CASE_INSENSITIVE_ORDER);
            case "updatedAt" -> Comparator.comparing(InventoryItemResponse::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(InventoryItemResponse::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
        };
    }

    private List<InventoryItemResponse> paginate(List<InventoryItemResponse> source, int page, int size) {
        if (source.isEmpty()) {
            return List.of();
        }
        int safeSize = Math.max(size, 1);
        int from = Math.min(Math.max(page, 0) * safeSize, source.size());
        int to = Math.min(from + safeSize, source.size());
        return source.subList(from, to);
    }

    private int totalPages(int totalElements, int size) {
        int safeSize = Math.max(size, 1);
        return (int) Math.ceil((double) totalElements / safeSize);
    }

    private long countParentProducts(List<InventoryItemResponse> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        Map<String, String> parent = new HashMap<>();
        Set<String> fallbackKeys = new LinkedHashSet<>();
        for (InventoryItemResponse item : items) {
            if (item == null) {
                continue;
            }
            List<String> keys = inventoryProductGroupKeys(item);
            if (keys.isEmpty()) {
                String fallbackKey = inventoryProductFallbackKey(item);
                if (fallbackKey != null && !fallbackKey.isBlank()) {
                    fallbackKeys.add(fallbackKey);
                }
                continue;
            }
            keys.forEach(key -> findProductGroupRoot(parent, key));
            for (int index = 1; index < keys.size(); index++) {
                unionProductGroupKeys(parent, keys.get(0), keys.get(index));
            }
        }
        Set<String> roots = parent.keySet().stream()
                .map(key -> findProductGroupRoot(parent, key))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        roots.addAll(fallbackKeys);
        return roots.size();
    }

    private List<String> inventoryProductGroupKeys(InventoryItemResponse item) {
        if (item == null) {
            return List.of();
        }
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        if (item.getProductIds() != null) {
            item.getProductIds().stream()
                    .filter(Objects::nonNull)
                    .map(id -> "product:" + id)
                    .forEach(keys::add);
        }
        if (item.getProductId() != null) {
            keys.add("product:" + item.getProductId());
        }
        String sku = normalizeSkuKey(firstNonBlank(item.getMarketplaceSku(), item.getVariantSku()));
        if (!sku.isBlank()) {
            keys.add("sku:" + sku);
        }
        return new ArrayList<>(keys);
    }

    private String findProductGroupRoot(Map<String, String> parent, String key) {
        parent.putIfAbsent(key, key);
        String current = parent.get(key);
        if (current.equals(key)) {
            return key;
        }
        String root = findProductGroupRoot(parent, current);
        parent.put(key, root);
        return root;
    }

    private void unionProductGroupKeys(Map<String, String> parent, String first, String second) {
        String firstRoot = findProductGroupRoot(parent, first);
        String secondRoot = findProductGroupRoot(parent, second);
        if (!firstRoot.equals(secondRoot)) {
            parent.put(secondRoot, firstRoot);
        }
    }

    private String inventoryProductFallbackKey(InventoryItemResponse item) {
        if (item == null) {
            return null;
        }
        if (item.getVariantId() != null) {
            return "variant:" + item.getVariantId();
        }
        String sku = normalizeSkuKey(firstNonBlank(item.getMarketplaceSku(), item.getVariantSku()));
        if (!sku.isBlank()) {
            return "sku:" + sku;
        }
        return "inventory:" + item.getId();
    }

    private boolean isLowStockResponse(InventoryItemResponse item) {
        return safeInt(item.getAvailableQuantity()) <= safeInt(item.getLowStockThreshold());
    }

    private void ensureInventoryItemsForExistingVariants() {
        List<ProductVariant> variants = variantRepository.findVariantsWithoutInventoryItems();
        if (variants.isEmpty()) {
            return;
        }

        Optional<Warehouse> sharedWarehouse = warehouseRepository.findFirstByNameAndDeletedAtIsNull(SHARED_WAREHOUSE_NAME)
                .filter(warehouse -> Boolean.TRUE.equals(warehouse.getIsActive()));
        Warehouse warehouse = sharedWarehouse
                .or(() -> warehouseRepository.findFirstByDeletedAtIsNullAndIsActiveTrueOrderByCreatedAtAsc())
                .orElse(null);
        if (warehouse == null) {
            return;
        }

        List<InventoryItem> items = variants.stream()
                .map(variant -> InventoryItem.builder()
                        .warehouse(warehouse)
                        .variant(variant)
                        .quantityOnHand(0)
                        .reservedQuantity(0)
                        .averageCost(variant.getCostPrice() == null ? BigDecimal.ZERO : variant.getCostPrice())
                        .lowStockThreshold(resolveLowStockThreshold(variant))
                        .build())
                .toList();
        inventoryItemRepository.saveAll(items);
    }

    private int resolveLowStockThreshold(ProductVariant variant) {
        if (variant == null || variant.getProduct() == null || variant.getProduct().getLowStockThreshold() == null) {
            return 5;
        }
        return variant.getProduct().getLowStockThreshold();
    }

    private String normalizeSkuKey(String sku) {
        return sku == null ? "" : sku.trim().toLowerCase(Locale.ROOT);
    }

    private String inventoryAggregateKey(InventoryItemResponse item, Map<UUID, ChannelSummary> channelSummaryByVariantId) {
        String sku = Optional.ofNullable(channelSummaryByVariantId.get(item.getVariantId()))
                .flatMap(summary -> summary.externalSkus().stream()
                        .map(this::normalizeSkuKey)
                        .filter(value -> !value.isBlank())
                        .findFirst())
                .orElseGet(() -> normalizeSkuKey(item.getVariantSku()));
        if (!sku.isBlank()) {
            return "sku:" + sku;
        }
        if (item.getVariantId() != null) {
            return "variant:" + item.getVariantId();
        }
        return "inventory:" + item.getId();
    }

    private String normalizeSkuValue(String sku) {
        if (sku == null || sku.isBlank()) {
            return null;
        }
        return sku.trim();
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private record ChannelSummary(List<UUID> channelIds,
                                  List<String> channelNames,
                                  List<PlatformType> platforms,
                                  List<String> externalSkus) {
    }

    private static class MutableChannelSummary {
        private final LinkedHashSet<UUID> channelIds = new LinkedHashSet<>();
        private final LinkedHashSet<String> channelNames = new LinkedHashSet<>();
        private final LinkedHashSet<PlatformType> platforms = new LinkedHashSet<>();
        private final LinkedHashSet<String> externalSkus = new LinkedHashSet<>();
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
            response.setMarketplaceSku(normalizeSkuValue(mapping.getExternalSku()));
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
        return aggregateAvailableVariantsBySku(variants);
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

        List<AvailableVariantDTO> variants = mappingByVariantId.values().stream()
                .map(mapping -> toMarketplaceAvailableVariant(mapping, itemByVariantId.get(mapping.getVariant().getId())))
                .toList();
        enrichAvailableVariantChannelInfo(variants, platform);
        return aggregateAvailableVariantsBySku(variants);
    }

    private AvailableVariantDTO toMarketplaceAvailableVariant(ChannelProductVariant mapping, InventoryItem item) {
        ProductVariant variant = mapping.getVariant();
        var channel = mapping.getChannelProduct().getChannel();
        BigDecimal averageCost = item != null && item.getAverageCost() != null
                ? item.getAverageCost()
                : variant.getCostPrice();
        Integer availableQuantity = item != null && item.getAvailableQuantity() != null
                ? item.getAvailableQuantity()
                : 0;

        return AvailableVariantDTO.builder()
                .variantId(variant.getId())
                .variantIds(List.of(variant.getId()))
                .sku(firstNonBlank(normalizeSkuValue(mapping.getExternalSku()), variant.getSku()))
                .marketplaceSku(normalizeSkuValue(mapping.getExternalSku()))
                .productName(variant.getProduct() == null ? variant.getName() : variant.getProduct().getName())
                .variantName(variant.getName())
                .unitPrice(variant.getPrice())
                .salePrice(variant.getPrice())
                .currentSalePrice(variant.getPrice())
                .averageCost(averageCost == null ? BigDecimal.ZERO : averageCost)
                .availableQuantity(availableQuantity)
                .channelId(channel.getId())
                .channelName(channel.getDisplayName())
                .platform(channel.getPlatform())
                .channelIds(channel.getId() == null ? List.of() : List.of(channel.getId()))
                .channelNames(channel.getDisplayName() == null || channel.getDisplayName().isBlank() ? List.of() : List.of(channel.getDisplayName()))
                .platforms(channel.getPlatform() == null ? List.of() : List.of(channel.getPlatform()))
                .mergedVariantCount(1)
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
        Map<UUID, MutableChannelSummary> summaryByVariantId = new HashMap<>();
        for (ChannelProductVariant mapping : mappings) {
            if (mapping.getVariant() == null || mapping.getVariant().getId() == null
                    || mapping.getChannelProduct() == null || mapping.getChannelProduct().getChannel() == null) {
                continue;
            }
            var channel = mapping.getChannelProduct().getChannel();
            MutableChannelSummary summary = summaryByVariantId.computeIfAbsent(
                    mapping.getVariant().getId(),
                    ignored -> new MutableChannelSummary()
            );
            if (channel.getId() != null && !summary.channelIds.contains(channel.getId())) {
                summary.channelIds.add(channel.getId());
            }
            if (channel.getDisplayName() != null && !channel.getDisplayName().isBlank()
                    && !summary.channelNames.contains(channel.getDisplayName())) {
                summary.channelNames.add(channel.getDisplayName());
            }
            if (channel.getPlatform() != null && !summary.platforms.contains(channel.getPlatform())) {
                summary.platforms.add(channel.getPlatform());
            }
            String externalSku = normalizeSkuValue(mapping.getExternalSku());
            if (externalSku != null) {
                summary.externalSkus.add(externalSku);
            }
        }

        for (AvailableVariantDTO variant : variants) {
            MutableChannelSummary summary = summaryByVariantId.get(variant.getVariantId());
            if (summary == null) {
                continue;
            }
            String marketplaceSku = firstOrNull(summary.externalSkus);
            if (marketplaceSku != null) {
                variant.setMarketplaceSku(marketplaceSku);
                variant.setSku(marketplaceSku);
            }
            variant.setVariantIds(variant.getVariantId() == null ? List.of() : List.of(variant.getVariantId()));
            variant.setChannelIds(new ArrayList<>(summary.channelIds));
            variant.setChannelNames(new ArrayList<>(summary.channelNames));
            variant.setPlatforms(new ArrayList<>(summary.platforms));
            variant.setMergedVariantCount(Math.max(summary.platforms.size(), 1));

            if (preferredPlatform != null && summary.platforms.contains(preferredPlatform)) {
                variant.setPlatform(preferredPlatform);
            } else {
                variant.setPlatform(summary.platforms.size() == 1 ? firstOrNull(summary.platforms) : null);
            }
            variant.setChannelId(summary.channelIds.size() == 1 ? firstOrNull(summary.channelIds) : null);
            variant.setChannelName(summary.channelNames.size() == 1 ? firstOrNull(summary.channelNames) : String.join(", ", summary.channelNames));
        }
    }

    private List<AvailableVariantDTO> aggregateAvailableVariantsBySku(List<AvailableVariantDTO> variants) {
        if (variants == null || variants.isEmpty()) {
            return List.of();
        }

        Map<String, List<AvailableVariantDTO>> bySku = variants.stream()
                .collect(Collectors.groupingBy(
                        this::availableVariantAggregateKey,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<AvailableVariantDTO> result = new ArrayList<>();
        for (List<AvailableVariantDTO> group : bySku.values()) {
            AvailableVariantDTO first = group.get(0);
            LinkedHashSet<UUID> variantIds = new LinkedHashSet<>();
            LinkedHashSet<UUID> channelIds = new LinkedHashSet<>();
            LinkedHashSet<String> channelNames = new LinkedHashSet<>();
            LinkedHashSet<PlatformType> platforms = new LinkedHashSet<>();

            int availableQuantity = 0;
            BigDecimal unitPrice = null;
            BigDecimal salePrice = null;
            BigDecimal averageCost = null;
            for (AvailableVariantDTO item : group) {
                if (item.getVariantId() != null) {
                    variantIds.add(item.getVariantId());
                }
                if (item.getVariantIds() != null) {
                    variantIds.addAll(item.getVariantIds());
                }
                if (item.getChannelId() != null) {
                    channelIds.add(item.getChannelId());
                }
                if (item.getChannelIds() != null) {
                    channelIds.addAll(item.getChannelIds());
                }
                if (item.getChannelName() != null && !item.getChannelName().isBlank()) {
                    channelNames.add(item.getChannelName());
                }
                if (item.getChannelNames() != null) {
                    channelNames.addAll(item.getChannelNames().stream()
                            .filter(name -> name != null && !name.isBlank())
                            .toList());
                }
                if (item.getPlatform() != null) {
                    platforms.add(item.getPlatform());
                }
                if (item.getPlatforms() != null) {
                    platforms.addAll(item.getPlatforms());
                }
                availableQuantity = Math.max(availableQuantity, safeInt(item.getAvailableQuantity()));
                unitPrice = firstPositive(unitPrice, item.getUnitPrice());
                salePrice = firstPositive(salePrice, firstNonNull(item.getSalePrice(), item.getCurrentSalePrice(), item.getUnitPrice()));
                averageCost = firstPositive(averageCost, item.getAverageCost());
            }

            String displaySku = firstNonBlank(first.getMarketplaceSku(), first.getSku());
            result.add(AvailableVariantDTO.builder()
                    .variantId(first.getVariantId())
                    .variantIds(new ArrayList<>(variantIds))
                    .sku(displaySku)
                    .marketplaceSku(first.getMarketplaceSku())
                    .productName(first.getProductName())
                    .variantName(first.getVariantName())
                    .unitPrice(firstNonNull(unitPrice, first.getUnitPrice(), BigDecimal.ZERO))
                    .salePrice(firstNonNull(salePrice, first.getSalePrice(), first.getCurrentSalePrice(), BigDecimal.ZERO))
                    .currentSalePrice(firstNonNull(salePrice, first.getCurrentSalePrice(), first.getSalePrice(), BigDecimal.ZERO))
                    .averageCost(firstNonNull(averageCost, first.getAverageCost(), BigDecimal.ZERO))
                    .availableQuantity(availableQuantity)
                    .channelId(channelIds.size() == 1 ? firstOrNull(channelIds) : null)
                    .channelName(channelNames.size() == 1 ? firstOrNull(channelNames) : String.join(", ", channelNames))
                    .platform(platforms.size() == 1 ? firstOrNull(platforms) : null)
                    .channelIds(new ArrayList<>(channelIds))
                    .channelNames(new ArrayList<>(channelNames))
                    .platforms(new ArrayList<>(platforms))
                    .mergedVariantCount(Math.max(variantIds.size(), 1))
                    .build());
        }
        return result;
    }

    private String availableVariantAggregateKey(AvailableVariantDTO variant) {
        String sku = normalizeSkuKey(firstNonBlank(variant.getMarketplaceSku(), variant.getSku()));
        if (!sku.isBlank()) {
            return "sku:" + sku;
        }
        if (variant.getVariantId() != null) {
            return "variant:" + variant.getVariantId();
        }
        return UUID.randomUUID().toString();
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private BigDecimal firstPositive(BigDecimal current, BigDecimal candidate) {
        if (current != null && current.compareTo(BigDecimal.ZERO) > 0) {
            return current;
        }
        if (candidate != null && candidate.compareTo(BigDecimal.ZERO) > 0) {
            return candidate;
        }
        return current == null ? candidate : current;
    }

    private <T> T firstOrNull(Collection<T> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.iterator().next();
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        return userRepository.findByEmail(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }


}
