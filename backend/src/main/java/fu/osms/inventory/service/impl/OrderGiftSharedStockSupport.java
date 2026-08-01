package fu.osms.inventory.service.impl;

import fu.osms.catalog.entity.ProductVariant;
import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.inventory.entity.InventoryIssue;
import fu.osms.inventory.entity.InventoryIssueItem;
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.inventory.entity.InventoryTransaction;
import fu.osms.inventory.repository.InventoryItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OrderGiftSharedStockSupport {

    private final InventoryItemRepository inventoryItemRepository;
    private final ChannelProductVariantRepository channelProductVariantRepository;

    Map<String, GiftGroup> giftGroups(Collection<InventoryIssueItem> gifts) {
        Map<String, GiftGroup> groups = new LinkedHashMap<>();
        gifts.stream()
                .sorted(Comparator.comparing(item -> item.getProductVariant().getId()))
                .forEach(item -> {
                    int quantity = safeQuantity(item.getQuantity());
                    List<ProductVariant> variants = resolveSharedStockVariants(item.getProductVariant());
                    String key = sharedGroupKey(variants);
                    GiftGroup existing = groups.get(key);
                    if (existing == null) {
                        groups.put(key, new GiftGroup(
                                variants,
                                item.getProductVariant().getId(),
                                quantity,
                                item.getUnitCost(),
                                item.getProductVariant().getSku()));
                    } else {
                        groups.put(key, existing.withQuantity(existing.quantity() + quantity));
                    }
                });
        return groups;
    }

    Map<String, TransactionGroup> transactionGroups(List<InventoryTransaction> transactions) {
        Map<String, TransactionGroup> groups = new LinkedHashMap<>();
        transactions.stream()
                .sorted(Comparator.comparing(transaction -> transaction.getVariant().getId()))
                .forEach(transaction -> {
                    List<ProductVariant> variants = resolveSharedStockVariants(transaction.getVariant());
                    String key = sharedGroupKey(variants);
                    TransactionGroup existing = groups.get(key);
                    if (existing == null) {
                        groups.put(key, new TransactionGroup(
                                variants,
                                new ArrayList<>(List.of(transaction)),
                                Math.abs(safeQuantity(transaction.getQuantityChange()))));
                    } else {
                        existing.transactions().add(transaction);
                        groups.put(key, existing.withQuantity(existing.quantity()
                                + Math.abs(safeQuantity(transaction.getQuantityChange()))));
                    }
                });
        return groups;
    }

    List<InventoryItem> lockGroup(InventoryIssue issue, List<ProductVariant> variants) {
        List<InventoryItem> inventories = new ArrayList<>();
        variants.stream()
                .sorted(Comparator.comparing(ProductVariant::getId))
                .forEach(variant -> inventories.add(inventoryItemRepository
                        .findByWarehouseIdAndVariantIdWithLock(issue.getWarehouse().getId(), variant.getId())
                        .orElseThrow(() -> new AppException(
                                ErrorCode.INVENTORY_ITEM_NOT_FOUND,
                                "SKU " + variant.getSku() + " không tồn tại trong Kho mặc định đa sàn"))));
        return inventories;
    }

    private List<ProductVariant> resolveSharedStockVariants(ProductVariant variant) {
        Set<String> externalSkuKeys = externalSkuKeys(
                channelProductVariantRepository.findActiveByVariantIdWithChannel(variant.getId()));
        if (externalSkuKeys.size() != 1) {
            return List.of(variant);
        }
        Map<UUID, ProductVariant> variantsById = new LinkedHashMap<>();
        variantsById.put(variant.getId(), variant);
        channelProductVariantRepository.findActiveByNormalizedExternalSkuInWithVariant(
                        new ArrayList<>(externalSkuKeys)).stream()
                .map(ChannelProductVariant::getVariant)
                .forEach(sharedVariant -> variantsById.putIfAbsent(sharedVariant.getId(), sharedVariant));
        return variantsById.values().stream()
                .sorted(Comparator.comparing(ProductVariant::getId))
                .toList();
    }

    private Set<String> externalSkuKeys(List<ChannelProductVariant> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            return Set.of();
        }
        return mappings.stream()
                .map(ChannelProductVariant::getExternalSku)
                .map(this::normalizeSku)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalizeSku(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String sharedGroupKey(List<ProductVariant> variants) {
        return variants.stream()
                .map(ProductVariant::getId)
                .sorted()
                .map(UUID::toString)
                .collect(Collectors.joining("|"));
    }

    private int safeQuantity(Integer value) {
        return value == null ? 0 : value;
    }

    record GiftGroup(
            List<ProductVariant> variants,
            UUID holderVariantId,
            int quantity,
            BigDecimal unitCost,
            String displaySku) {

        private GiftGroup withQuantity(int newQuantity) {
            return new GiftGroup(variants, holderVariantId, newQuantity, unitCost, displaySku);
        }
    }

    record TransactionGroup(
            List<ProductVariant> variants,
            List<InventoryTransaction> transactions,
            int quantity) {

        private TransactionGroup withQuantity(int newQuantity) {
            return new TransactionGroup(variants, transactions, newQuantity);
        }
    }
}
