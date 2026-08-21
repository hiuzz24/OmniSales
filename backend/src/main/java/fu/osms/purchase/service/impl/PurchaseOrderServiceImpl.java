package fu.osms.purchase.service.impl;

import fu.osms.auth.entity.User;
import fu.osms.auth.repository.UserRepository;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.catalog.util.ProductCostPolicy;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.common.dto.PageResponse;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.inventory.entity.InventoryReceipt;
import fu.osms.inventory.entity.Supplier;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.repository.SupplierRepository;
import fu.osms.purchase.dto.*;
import fu.osms.purchase.entity.PurchaseOrder;import fu.osms.purchase.entity.PurchaseOrderItem;
import fu.osms.purchase.enums.PurchaseOrderStatus;
import fu.osms.purchase.repository.PurchaseOrderItemRepository;
import fu.osms.purchase.repository.PurchaseOrderRepository;
import fu.osms.purchase.service.PurchaseOrderService;
import fu.osms.sync.service.MarketplaceWarehouseConsistencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.security.SecureRandom;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
@Service
@RequiredArgsConstructor
public class PurchaseOrderServiceImpl implements PurchaseOrderService {
    private static final SecureRandom ORDER_CODE_RANDOM = new SecureRandom();
    private static final List<PlatformType> PURCHASE_PLATFORMS = List.of(
            PlatformType.SHOPIFY, PlatformType.LAZADA, PlatformType.TIKTOK
    );

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final SupplierRepository supplierRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final ChannelRepository channelRepository;
    private final ChannelProductVariantRepository channelVariantRepository;
    private final UserRepository userRepository;
    private final MarketplaceWarehouseConsistencyService warehouseConsistencyService;

    @Override
    @Transactional
    public PurchaseOrderResponse create(PurchaseOrderRequest request, UUID userId) {
        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        PurchaseOrder order = PurchaseOrder.builder()
                .orderCode(resolveOrderCode(request.getOrderCode()))
                .supplier(resolveSupplier(request.getSupplierId()))
                .warehouse(warehouseConsistencyService.resolveMasterWarehouse())
                .status(Boolean.TRUE.equals(request.getIsDraft())
                        ? PurchaseOrderStatus.DRAFT : PurchaseOrderStatus.SENT_TO_SUPPLIER)
                .orderDate(OffsetDateTime.now())
                .expectedReceiptDate(request.getExpectedReceiptDate())
                .paymentMethod(request.getPaymentMethod())
                .notes(request.getNotes())
                .createdBy(creator)
                .sentAt(Boolean.TRUE.equals(request.getIsDraft()) ? null : OffsetDateTime.now())
                .build();
        replaceItems(order, request.getItems());
        return toResponse(purchaseOrderRepository.save(order));
    }

    @Override
    @Transactional
    public PurchaseOrderResponse updateDraft(UUID id, PurchaseOrderRequest request) {
        PurchaseOrder order = requireOrder(id);
        if (order.getStatus() != PurchaseOrderStatus.DRAFT
                && order.getStatus() != PurchaseOrderStatus.SENT_TO_SUPPLIER) {
            throw new AppException(ErrorCode.INVALID_REQUEST,
                    "Chỉ được sửa đơn khi ở trạng thái Nháp hoặc Đã gửi NCC.");
        }
        order.setSupplier(resolveSupplier(request.getSupplierId()));
        order.setExpectedReceiptDate(request.getExpectedReceiptDate());
        order.setPaymentMethod(request.getPaymentMethod());
        order.setNotes(request.getNotes());
        order.getItems().clear();
        purchaseOrderItemRepository.deleteByPurchaseOrderId(order.getId());
        replaceItems(order, request.getItems());
        return toResponse(purchaseOrderRepository.save(order));
    }

    @Override
    @Transactional
    public PurchaseOrderResponse sendToSupplier(UUID id) {
        PurchaseOrder order = requireOrder(id);
        if (order.getStatus() != PurchaseOrderStatus.DRAFT
                && order.getStatus() != PurchaseOrderStatus.SENT_TO_SUPPLIER) {
            throw new AppException(ErrorCode.INVALID_REQUEST,
                    "Chỉ đơn Nháp hoặc Đã gửi NCC mới có thể gửi nhà cung cấp.");
        }
        order.setStatus(PurchaseOrderStatus.SENT_TO_SUPPLIER);
        order.setSentAt(OffsetDateTime.now());
        return toResponse(purchaseOrderRepository.save(order));
    }

    @Override
    @Transactional
    public PurchaseOrderResponse cancel(UUID id) {
        PurchaseOrder order = requireOrder(id);
        if (order.getStatus() == PurchaseOrderStatus.COMPLETED) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Không thể hủy đơn đặt hàng đã hoàn thành.");
        }
        if (order.getStatus() == PurchaseOrderStatus.CANCELLED) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Đơn đặt hàng đã được hủy trước đó.");
        }
        if (order.getStatus() == PurchaseOrderStatus.RECEIVING) {
            throw new AppException(ErrorCode.INVALID_REQUEST,
                    "Không thể hủy đơn khi đang giao hàng hoặc đã tạo phiếu nhập kho.");
        }
        order.setStatus(PurchaseOrderStatus.CANCELLED);
        return toResponse(purchaseOrderRepository.save(order));
    }

    @Override
    @Transactional(readOnly = true)
    public PurchaseOrderResponse getById(UUID id) {
        return toResponse(requireOrder(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PurchaseOrderResponse> getAll(int page, int size, PurchaseOrderStatus status) {
        Page<PurchaseOrder> result = status == null
                ? purchaseOrderRepository.findAllWithDetails(PageRequest.of(page, size))
                : purchaseOrderRepository.findAllWithDetailsByStatus(status, PageRequest.of(page, size));
        List<PurchaseOrderResponse> content = result.getContent().stream().map(this::toResponse).toList();
        return PageResponse.<PurchaseOrderResponse>builder()
                .content(content).page(page).size(size)
                .totalElements(result.getTotalElements()).totalPages(result.getTotalPages())
                .first(result.isFirst()).last(result.isLast()).build();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> getStatistics() {
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("totalCount", purchaseOrderRepository.count());
        for (PurchaseOrderStatus status : PurchaseOrderStatus.values()) {
            result.put(status.name(), purchaseOrderRepository.countByStatus(status));
        }
        return result;
    }

    @Override
    @Transactional
    public PurchaseOrderFormOptionsResponse getFormOptions() {
        Warehouse warehouse = warehouseConsistencyService.resolveMasterWarehouse();

        List<PurchaseOrderFormOptionsResponse.MarketplaceWarehouseOption> marketplaceWarehouses =
                marketplaceWarehouses(warehouse);
        Map<PlatformType, String> externalWarehouseIds = marketplaceWarehouses.stream()
                .collect(Collectors.toMap(
                        PurchaseOrderFormOptionsResponse.MarketplaceWarehouseOption::getPlatform,
                        PurchaseOrderFormOptionsResponse.MarketplaceWarehouseOption::getExternalWarehouseId,
                        (first, ignored) -> first,
                        LinkedHashMap::new));

        PurchaseOrderFormOptionsResponse.WarehouseOption warehouseOption =
                PurchaseOrderFormOptionsResponse.WarehouseOption.builder()
                        .id(warehouse.getId())
                        .name(warehouse.getName())
                        .address(warehouse.getAddress())
                        .shopifyLocationId(externalWarehouseIds.get(PlatformType.SHOPIFY))
                        .lazadaWarehouseCode(externalWarehouseIds.get(PlatformType.LAZADA))
                        .tiktokWarehouseId(externalWarehouseIds.get(PlatformType.TIKTOK))
                        .marketplaceWarehouses(marketplaceWarehouses)
                        .build();

        return PurchaseOrderFormOptionsResponse.builder()
                .orderCode(generateOrderCode())
                .suppliers(List.of())
                .warehouse(warehouseOption)
                .productGroups(purchasableProductGroups(warehouse))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public String generateOrderCode() {
        String prefix = currentOrderCodePrefix();
        for (int attempt = 0; attempt < 30; attempt++) {
            String candidate = prefix + String.format("%06d", ORDER_CODE_RANDOM.nextInt(1_000_000));
            if (!purchaseOrderRepository.existsByOrderCode(candidate)) {
                return candidate;
            }
        }
        throw new AppException(ErrorCode.INVALID_REQUEST, "Không thể sinh mã đơn đặt hàng duy nhất. Vui lòng thử lại.");
    }

    @Override
    @Transactional
    public PurchaseOrderResponse confirmShipping(UUID id) {
        PurchaseOrder order = requireOrder(id);
        requireStatus(order, PurchaseOrderStatus.SENT_TO_SUPPLIER,
                "Chỉ đơn ở trạng thái Đã gửi NCC mới có thể xác nhận vận chuyển.");
        order.setStatus(PurchaseOrderStatus.RECEIVING);
        order.setReceivingAt(OffsetDateTime.now());
        return toResponse(purchaseOrderRepository.save(order));
    }

    @Override
    @Transactional
    public void completeFromReceipt(UUID purchaseOrderId) {
        completeFromReceiptWithResult(purchaseOrderId);
    }

    @Override
    @Transactional
    public Optional<AutoCreatedOrderResult> completeFromReceiptWithResult(UUID purchaseOrderId) {
        PurchaseOrder order = requireOrder(purchaseOrderId);
        if (order.getStatus() == PurchaseOrderStatus.COMPLETED
                || order.getStatus() == PurchaseOrderStatus.CANCELLED) {
            return Optional.empty();
        }

        // A purchase order is complete only when every ordered variant has been
        // received in full (cumulatively) across all confirmed receipts. Partial
        // receipts leave the order open for the next delivery batch.
        if (!isFullyReceived(order)) {
            return Optional.empty();
        }

        order.setStatus(PurchaseOrderStatus.COMPLETED);
        order.setCompletedAt(OffsetDateTime.now());
        purchaseOrderRepository.save(order);

        return Optional.empty();
    }

    @Override
    @Transactional
    public PurchaseOrderResponse updateEvidence(UUID id, String evidenceUrl) {
        PurchaseOrder order = requireOrder(id);
        order.setEvidenceUrl(evidenceUrl);
        return toResponse(purchaseOrderRepository.save(order));
    }

    private boolean isFullyReceived(PurchaseOrder order) {
        for (PurchaseOrderItem item : order.getItems()) {
            int received = item.getActualQuantity() == null ? 0 : item.getActualQuantity();
            if (received < item.getQuantity()) {
                return false;
            }
        }
        return true;
    }

    private void replaceItems(PurchaseOrder order, List<PurchaseOrderItemRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Đơn đặt hàng phải có ít nhất một sản phẩm.");
        }
        Set<UUID> ids = new HashSet<>();
        Map<String, PurchaseLine> linesBySharedGroup = new LinkedHashMap<>();
        for (PurchaseOrderItemRequest request : requests) {
            if (!ids.add(request.getVariantId())) {
                throw new AppException(ErrorCode.VALIDATION_FAILED, "Sản phẩm bị trùng trong đơn đặt hàng.");
            }
            ProductVariant variant = variantRepository.findById(request.getVariantId())
                    .filter(item -> Boolean.TRUE.equals(item.getIsActive()) && item.getDeletedAt() == null)
                    .orElseThrow(() -> new AppException(ErrorCode.VARIANT_NOT_FOUND));
            BigDecimal unitCost = request.getUnitCost() == null ? BigDecimal.ZERO : request.getUnitCost();
            List<ProductVariant> sharedVariants = resolveSharedStockVariants(variant);
            String sharedGroupKey = sharedStockGroupKey(sharedVariants);
            PurchaseLine existingLine = linesBySharedGroup.get(sharedGroupKey);
            if (existingLine != null) {
                if (existingLine.terms().matches(request.getQuantity(), unitCost)) {
                    continue;
                }
                throw new AppException(ErrorCode.VALIDATION_FAILED,
                        "Các sản phẩm con liên kết cùng SKU phải có cùng số lượng và đơn giá.");
            }

            String canonicalSku = canonicalMarketplaceSku(sharedVariants, variant);
            ProductVariant representative = chooseRepresentativeVariant(sharedVariants, canonicalSku, variant);
            linesBySharedGroup.put(
                    sharedGroupKey,
                    new PurchaseLine(
                            representative,
                            new PurchaseLineTerms(request.getQuantity(), unitCost))
            );
        }

        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseLine line : linesBySharedGroup.values()) {
            order.addItem(PurchaseOrderItem.builder()
                    .variant(line.variant())
                    .quantity(line.terms().quantity())
                    .unitCost(line.terms().unitCost())
                    .build());
            total = total.add(line.terms().unitCost()
                    .multiply(BigDecimal.valueOf(line.terms().quantity())));
        }
        order.setTotalAmount(total);
    }

    private List<ProductVariant> resolveSharedStockVariants(ProductVariant variant) {
        Set<String> externalSkuKeys = externalSkuKeys(
                channelVariantRepository.findActiveByVariantIdWithChannel(variant.getId()));
        if (externalSkuKeys.size() != 1) {
            return List.of(variant);
        }

        Map<UUID, ProductVariant> sharedVariants = new LinkedHashMap<>();
        sharedVariants.put(variant.getId(), variant);
        channelVariantRepository.findActiveByNormalizedExternalSkuInWithVariant(
                        new ArrayList<>(externalSkuKeys)).stream()
                .map(ChannelProductVariant::getVariant)
                .forEach(item -> sharedVariants.putIfAbsent(item.getId(), item));
        return new ArrayList<>(sharedVariants.values());
    }

    private String sharedStockGroupKey(List<ProductVariant> variants) {
        Set<String> externalSkuKeys = externalSkuKeys(
                channelVariantRepository.findActiveByVariantIdInWithChannel(
                        variants.stream().map(ProductVariant::getId).toList()));
        if (externalSkuKeys.size() == 1) {
            return "external:" + externalSkuKeys.iterator().next();
        }
        return variants.stream()
                .map(ProductVariant::getId)
                .map(UUID::toString)
                .sorted()
                .map(id -> "variant:" + id)
                .collect(Collectors.joining("|"));
    }

    private String canonicalMarketplaceSku(List<ProductVariant> variants, ProductVariant fallback) {
        List<UUID> variantIds = variants.stream().map(ProductVariant::getId).toList();
        List<ChannelProductVariant> mappings =
                channelVariantRepository.findActiveByVariantIdInWithChannel(variantIds);
        Set<String> externalSkuKeys = externalSkuKeys(mappings);
        if (externalSkuKeys.size() != 1) {
            return fallback.getSku();
        }
        String canonicalKey = externalSkuKeys.iterator().next();
        return mappings.stream()
                .map(ChannelProductVariant::getExternalSku)
                .filter(this::hasText)
                .map(String::trim)
                .filter(value -> canonicalKey.equals(normalizeSku(value)))
                .findFirst()
                .orElse(fallback.getSku());
    }

    private Set<String> externalSkuKeys(List<ChannelProductVariant> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            return Set.of();
        }
        return mappings.stream()
                .map(ChannelProductVariant::getExternalSku)
                .filter(this::hasText)
                .map(this::normalizeSku)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private ProductVariant chooseRepresentativeVariant(
            List<ProductVariant> variants,
            String canonicalSku,
            ProductVariant fallback) {
        if (hasText(canonicalSku)) {
            return variants.stream()
                    .filter(variant -> canonicalSku.equalsIgnoreCase(variant.getSku()))
                    .findFirst()
                    .orElse(fallback);
        }
        return fallback;
    }

    private Supplier resolveSupplier(UUID id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.SUPPLIER_NOT_FOUND));
    }

    private PurchaseOrder requireOrder(UUID id) {
        return purchaseOrderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn đặt hàng."));
    }

    private void requireStatus(PurchaseOrder order, PurchaseOrderStatus status, String message) {
        if (order.getStatus() != status) {
            throw new AppException(ErrorCode.ORDER_STATUS_INVALID_TRANSITION, message);
        }
    }

    private String resolveOrderCode(String requestedCode) {
        if (requestedCode != null
                && requestedCode.startsWith(currentOrderCodePrefix())
                && !purchaseOrderRepository.existsByOrderCode(requestedCode)) {
            return requestedCode;
        }
        return generateOrderCode();
    }

    private String currentOrderCodePrefix() {
        return "MĐH-" + LocalDate.now().getYear() + "-";
    }

    private List<PurchaseOrderFormOptionsResponse.MarketplaceWarehouseOption> marketplaceWarehouses(
            Warehouse warehouse) {
        return channelRepository.findByDeletedAtIsNull().stream()
                .filter(channel -> PURCHASE_PLATFORMS.contains(channel.getPlatform()))
                .filter(channel -> Boolean.TRUE.equals(channel.getSyncEnabled()))
                .filter(channel -> "CONNECTED".equalsIgnoreCase(channel.getStatus()))
                .filter(channel -> belongsToWarehouse(channel, warehouse.getId()))
                .map(channel -> {
                    String externalWarehouseId = externalWarehouseId(channel);
                    if (!hasText(externalWarehouseId)) {
                        return null;
                    }
                    return PurchaseOrderFormOptionsResponse.MarketplaceWarehouseOption.builder()
                            .channelId(channel.getId())
                            .channelName(channel.getDisplayName())
                            .platform(channel.getPlatform())
                            .externalWarehouseId(externalWarehouseId)
                            .externalWarehouseKey(externalWarehouseKey(channel.getPlatform()))
                            .build();
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(option -> option.getPlatform().name()))
                .toList();
    }

    private List<PurchaseOrderFormOptionsResponse.ProductGroupOption> purchasableProductGroups(
            Warehouse warehouse) {
        List<ChannelProductVariant> mappings = channelVariantRepository.findAllActiveWithVariantAndChannel();
        List<ChannelProductVariant> supportedMappings = mappings.stream()
                .filter(mapping -> PURCHASE_PLATFORMS.contains(mapping.getChannelProduct().getChannel().getPlatform()))
                .toList();
        if (supportedMappings.isEmpty()) {
            return List.of();
        }

        Set<UUID> mappedVariantIds = supportedMappings.stream()
                .map(mapping -> mapping.getVariant().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, InventoryItem> inventoryByVariantId = inventoryItemRepository
                .findByWarehouseIdAndVariantIdIn(warehouse.getId(), mappedVariantIds).stream()
                .collect(Collectors.toMap(item -> item.getVariant().getId(), Function.identity(), (first, ignored) -> first));

        Map<String, Map<UUID, List<ChannelProductVariant>>> mappingsByGroupAndVariant = new LinkedHashMap<>();
        Map<String, String> displaySkuByGroup = new LinkedHashMap<>();
        for (ChannelProductVariant mapping : supportedMappings) {
            ProductVariant variant = mapping.getVariant();
            String externalSku = hasText(mapping.getExternalSku())
                    ? mapping.getExternalSku().trim()
                    : null;
            String groupKey = externalSku == null
                    ? "variant:" + variant.getId()
                    : "external:" + normalizeSku(externalSku);
            displaySkuByGroup.putIfAbsent(
                    groupKey,
                    externalSku == null ? variant.getSku() : externalSku);
            mappingsByGroupAndVariant
                    .computeIfAbsent(groupKey, ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(variant.getId(), ignored -> new ArrayList<>())
                    .add(mapping);
        }

        Map<String, List<PurchaseOrderFormOptionsResponse.ProductVariantOption>> groupedVariants =
                new LinkedHashMap<>();
        for (Map.Entry<String, Map<UUID, List<ChannelProductVariant>>> groupEntry
                : mappingsByGroupAndVariant.entrySet()) {
            for (List<ChannelProductVariant> variantMappings : groupEntry.getValue().values()) {
                ProductVariant variant = variantMappings.get(0).getVariant();
                InventoryItem inventoryItem = inventoryByVariantId.get(variant.getId());
                List<PurchaseOrderFormOptionsResponse.MarketplaceVariantSource> sources =
                        variantMappings.stream()
                                .map(mapping -> {
                                    Channel channel = mapping.getChannelProduct().getChannel();
                                    return PurchaseOrderFormOptionsResponse.MarketplaceVariantSource.builder()
                                            .channelProductVariantId(mapping.getId())
                                            .channelId(channel.getId())
                                            .channelName(channel.getDisplayName())
                                            .platform(channel.getPlatform())
                                            .externalProductId(mapping.getChannelProduct().getExternalProductId())
                                            .externalVariantId(mapping.getExternalVariantId())
                                            .externalSku(mapping.getExternalSku())
                                            .externalWarehouseId(externalWarehouseId(channel))
                                            .build();
                                })
                                .toList();
                PurchaseOrderFormOptionsResponse.ProductVariantOption option =
                        PurchaseOrderFormOptionsResponse.ProductVariantOption.builder()
                                .variantId(variant.getId())
                                .productId(variant.getProduct().getId())
                                .sku(variant.getSku())
                                .marketplaceSku(displaySkuByGroup.get(groupEntry.getKey()))
                                .productName(variant.getProduct().getName())
                                .variantName(variant.getName())
                                .price(variant.getPrice())
                                .unitPrice(variant.getPrice())
                                .averageCost(inventoryItem != null && inventoryItem.getAverageCost() != null
                                        ? inventoryItem.getAverageCost()
                                        : Optional.ofNullable(variant.getCostPrice()).orElse(BigDecimal.ZERO))
                                .marketplaceSources(sources)
                                .build();
                groupedVariants.computeIfAbsent(groupEntry.getKey(), ignored -> new ArrayList<>())
                        .add(option);
            }
        }

        return groupedVariants.entrySet().stream()
                .map(entry -> {
                    List<PurchaseOrderFormOptionsResponse.ProductVariantOption> variants = entry.getValue().stream()
                            .sorted(Comparator.comparing(PurchaseOrderFormOptionsResponse.ProductVariantOption::getProductName)
                                    .thenComparing(option -> Optional.ofNullable(option.getVariantName()).orElse("")))
                            .toList();
                    List<PlatformType> platforms = variants.stream()
                            .flatMap(variant -> variant.getMarketplaceSources().stream())
                            .map(PurchaseOrderFormOptionsResponse.MarketplaceVariantSource::getPlatform)
                            .distinct()
                            .sorted(Comparator.comparing(Enum::name))
                            .toList();
                    return PurchaseOrderFormOptionsResponse.ProductGroupOption.builder()
                            .groupKey(entry.getKey())
                            .sku(displaySkuByGroup.get(entry.getKey()))
                            .productName(variants.get(0).getProductName())
                            .platforms(platforms)
                            .variants(variants)
                            .build();
                })
                .sorted(Comparator.comparing(PurchaseOrderFormOptionsResponse.ProductGroupOption::getSku,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    private boolean belongsToWarehouse(Channel channel, UUID warehouseId) {
        Object configuredWarehouseId = channel.getMetadata() == null
                ? null : channel.getMetadata().get("defaultWarehouseId");
        return configuredWarehouseId == null || warehouseId.toString().equals(configuredWarehouseId.toString());
    }

    private String externalWarehouseId(Channel channel) {
        if (channel.getMetadata() == null) {
            return null;
        }
        return switch (channel.getPlatform()) {
            case SHOPIFY -> firstNonBlank(channel, "shopifyLocationId", "defaultShopifyLocationId");
            case LAZADA -> firstNonBlank(channel, "lazadaWarehouseCode", "defaultLazadaWarehouseCode");
            case TIKTOK -> firstNonBlank(channel, "tiktokWarehouseId", "defaultTikTokWarehouseId");
            default -> null;
        };
    }

    private String externalWarehouseKey(PlatformType platform) {
        return switch (platform) {
            case SHOPIFY -> "shopifyLocationId";
            case LAZADA -> "lazadaWarehouseCode";
            case TIKTOK -> "tiktokWarehouseId";
            default -> null;
        };
    }

    private String firstNonBlank(Channel channel, String... keys) {
        for (String key : keys) {
            Object value = channel.getMetadata().get(key);
            if (value != null && hasText(value.toString())) {
                return value.toString().trim();
            }
        }
        return null;
    }

    private String normalizeSku(String value) {
        return hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private BigDecimal resolveCurrentCost(Map<UUID, BigDecimal> currentCostByVariant, ProductVariant variant) {
        BigDecimal inventoryCost = currentCostByVariant.get(variant.getId());
        if (ProductCostPolicy.isPositive(inventoryCost)) {
            return inventoryCost;
        }
        if (ProductCostPolicy.isPositive(variant.getCostPrice())) {
            return variant.getCostPrice();
        }
        return variant.getPrice() != null ? variant.getPrice() : BigDecimal.ZERO;
    }

    private PurchaseOrderResponse toResponse(PurchaseOrder order) {
        Map<String, PurchaseOrderItem> logicalItems = new LinkedHashMap<>();
        Map<String, List<ProductVariant>> sharedVariantsByGroup = new LinkedHashMap<>();
        if (order.getItems() != null) {
            for (PurchaseOrderItem item : order.getItems()) {
                List<ProductVariant> sharedVariants = resolveSharedStockVariants(item.getVariant());
                String groupKey = sharedStockGroupKey(sharedVariants);
                logicalItems.putIfAbsent(groupKey, item);
                sharedVariantsByGroup.putIfAbsent(groupKey, sharedVariants);
            }
        }

        List<UUID> variantIds = sharedVariantsByGroup.values().stream()
                .flatMap(Collection::stream)
                .map(ProductVariant::getId)
                .distinct()
                .toList();
        Map<UUID, List<ChannelProductVariant>> marketplaceMappings = variantIds.isEmpty()
                ? Map.of()
                : channelVariantRepository.findActiveByVariantIdInWithChannel(variantIds).stream()
                .filter(mapping -> PURCHASE_PLATFORMS.contains(
                        mapping.getChannelProduct().getChannel().getPlatform()))
                .collect(Collectors.groupingBy(
                        mapping -> mapping.getVariant().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        Map<UUID, BigDecimal> currentCostByVariant = variantIds.isEmpty()
                ? Map.of()
                : inventoryItemRepository
                .findByWarehouseIdAndVariantIdIn(order.getWarehouse().getId(), variantIds)
                .stream()
                .filter(item -> ProductCostPolicy.isPositive(item.getAverageCost()))
                .collect(Collectors.toMap(
                        item -> item.getVariant().getId(),
                        InventoryItem::getAverageCost,
                        (first, second) -> first));

        List<PurchaseOrderItemResponse> items = logicalItems.entrySet().stream()
                .map(entry -> {
                    PurchaseOrderItem item = entry.getValue();
                    List<ProductVariant> sharedVariants =
                            sharedVariantsByGroup.getOrDefault(entry.getKey(), List.of(item.getVariant()));
                    List<ChannelProductVariant> mappings = sharedVariants.stream()
                            .flatMap(variant -> marketplaceMappings
                                    .getOrDefault(variant.getId(), List.of()).stream())
                            .toList();
                    String marketplaceSku = mappings.stream()
                            .map(ChannelProductVariant::getExternalSku)
                            .filter(this::hasText)
                            .findFirst()
                            .orElse(item.getVariant().getSku());
                    List<PlatformType> platforms = mappings.stream()
                            .map(mapping -> mapping.getChannelProduct().getChannel().getPlatform())
                            .distinct()
                            .sorted(Comparator.comparing(Enum::name))
                            .toList();
                    return PurchaseOrderItemResponse.builder()
                            .id(item.getId()).variantId(item.getVariant().getId())
                            .sku(marketplaceSku).marketplaceSku(marketplaceSku)
                            .productName(item.getVariant().getProduct().getName())
                            .variantName(item.getVariant().getName()).platforms(platforms)
                            .quantity(item.getQuantity())
                            .receivedQuantity(item.getActualQuantity() == null ? 0 : item.getActualQuantity())
                            .actualQuantity(item.getActualQuantity())
                            .unitCost(item.getUnitCost())
                            .totalCost(item.getUnitCost().multiply(BigDecimal.valueOf(item.getQuantity())))
                            .salePrice(item.getVariant().getPrice())
                            .costPrice(resolveCurrentCost(currentCostByVariant, item.getVariant()))
                            .build();
                })
                .toList();
        List<InventoryReceipt> receipts = order.getReceipts() == null
                ? List.of()
                : new ArrayList<>(order.getReceipts());
        InventoryReceipt latestReceipt = receipts.stream()
                .max(Comparator.comparing(InventoryReceipt::getCreatedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
        List<PurchaseOrderResponse.ReceiptSummary> receiptSummaries = receipts.stream()
                .sorted(Comparator.comparing(InventoryReceipt::getCreatedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(receipt -> PurchaseOrderResponse.ReceiptSummary.builder()
                        .id(receipt.getId())
                        .receiptCode(receipt.getReceiptCode())
                        .build())
                .toList();
        return PurchaseOrderResponse.builder()
                .id(order.getId()).orderCode(order.getOrderCode())
                .supplierId(order.getSupplier().getId()).supplierName(order.getSupplier().getName())
                .supplierTaxCode(order.getSupplier().getTaxCode())
                .warehouseId(order.getWarehouse().getId()).warehouseName(order.getWarehouse().getName())
                .warehouseAddress(order.getWarehouse().getAddress())
                .status(order.getStatus()).orderDate(order.getOrderDate())
                .expectedReceiptDate(order.getExpectedReceiptDate()).paymentMethod(order.getPaymentMethod())
                .totalAmount(order.getTotalAmount()).notes(order.getNotes())
                .createdById(order.getCreatedBy() == null ? null : order.getCreatedBy().getId())
                .createdByName(order.getCreatedBy() == null ? null : order.getCreatedBy().getFullName())
                .receiptId(latestReceipt == null ? null : latestReceipt.getId())
                .receiptCode(latestReceipt == null ? null : latestReceipt.getReceiptCode())
                .receiptCount(receipts.size())
                .receipts(receiptSummaries)
                .evidenceUrl(order.getEvidenceUrl())
                .sentAt(order.getSentAt()).receivingAt(order.getReceivingAt())
                .completedAt(order.getCompletedAt())
                .createdAt(order.getCreatedAt()).updatedAt(order.getUpdatedAt()).items(items)
                .build();
    }

    private record PurchaseLineTerms(Integer quantity, BigDecimal unitCost) {
        private boolean matches(Integer otherQuantity, BigDecimal otherUnitCost) {
            return Objects.equals(quantity, otherQuantity) && unitCost.compareTo(otherUnitCost) == 0;
        }
    }

    private record PurchaseLine(ProductVariant variant, PurchaseLineTerms terms) {
    }
}
