package fu.osms.sync.lazada.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.channel.entity.ChannelCredential;
import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.common.enums.SyncStatus;
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.inventory.repository.InventoryIssueRepository;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.repository.StockReceiveRepository;
import fu.osms.sync.lazada.dto.LazadaInventorySyncResult;
import fu.osms.sync.lazada.service.LazadaAuthorizedApiClient;
import fu.osms.channel.token.service.ChannelTokenService;
import fu.osms.sync.lazada.service.LazadaInventoryUpdateService;
import fu.osms.sync.service.MarketplaceStockQuantityResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LazadaInventoryUpdateServiceImpl implements LazadaInventoryUpdateService {

    private static final int SKU_BATCH_SIZE = 50;
    private static final String API_PATH = "/product/stock/sellable/update";
    private static final String WAREHOUSE_CODE_MARKER = "LAZADA_WAREHOUSE_CODE=";

    private final LazadaAuthorizedApiClient lazadaApiClient;
    private final ChannelTokenService channelTokenService;
    private final ObjectMapper objectMapper;
    private final ChannelCredentialRepository credentialRepository;
    private final ChannelProductVariantRepository channelProductVariantRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final StockReceiveRepository stockReceiveRepository;
    private final InventoryIssueRepository inventoryIssueRepository;
    private final MarketplaceStockQuantityResolver marketplaceStockQuantityResolver;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public LazadaInventorySyncResult syncChangedSellableStock(UUID channelId,
                                                              OffsetDateTime changedSince,
                                                              OffsetDateTime changedUntil,
                                                              Collection<UUID> productChangedVariantIds) {
        log.info(
                "[LazadaStockSync] Start channelId={} api={} changedSince={} mode={} productChangedVariantIds={}",
                channelId,
                API_PATH,
                changedSince,
                changedSince == null ? "FULL_BASELINE" : "INCREMENTAL",
                productChangedVariantIds
        );
        ChannelCredential credential = credentialRepository.findByChannelIdAndConnectionState(channelId, "CONNECTED")
                .orElseThrow(() -> new IllegalStateException("Kenh Lazada chua co token ket noi."));
        channelTokenService.getValidToken(channelId);
        if (credential.getAccessToken() == null || credential.getAccessToken().isBlank()) {
            throw new IllegalStateException("Kenh Lazada chua co access_token. Vui long ket noi lai bang OAuth Lazada.");
        }
        UUID defaultWarehouseId = resolveDefaultWarehouseId(credential);
        String defaultWarehouseCode = resolveDefaultWarehouseCode(credential, defaultWarehouseId);

        ChangedScope changedScope = resolveChangedScope(changedSince, changedUntil, productChangedVariantIds);
        log.info(
                "[LazadaStockSync] Resolved changed scope channelId={} variantCount={} warehouseCount={} variantIds={} warehouseIds={}",
                channelId,
                changedScope.variantIds().size(),
                changedScope.warehouseIds().size(),
                changedScope.variantIds(),
                changedScope.warehouseIds()
        );
        if (changedSince != null && changedScope.variantIds().isEmpty()) {
            log.info("[LazadaStockSync] No stock document changes found since {}. Skip Lazada API call.", changedSince);
            return new LazadaInventorySyncResult(0, 0, 0);
        }

        boolean scopedFullSync = changedSince == null && !changedScope.variantIds().isEmpty();
        List<ChannelProductVariant> mappings = changedSince == null && !scopedFullSync
                ? channelProductVariantRepository.findActiveByChannelIdWithVariant(channelId)
                : channelProductVariantRepository.findActiveByChannelIdAndVariantIdInWithVariant(
                        channelId,
                        new ArrayList<>(changedScope.variantIds())
                );
        if (mappings.isEmpty()) {
            log.info(
                    "[LazadaStockSync] No Lazada channel variant mapping found channelId={} changedVariantIds={}",
                    channelId,
                    changedScope.variantIds()
            );
            return new LazadaInventorySyncResult(0, 0, changedScope.warehouseIds().size());
        }
        log.info("[LazadaStockSync] Loaded Lazada mappings channelId={} mappingCount={}", channelId, mappings.size());

        List<UUID> variantIds = mappings.stream()
                .map(mapping -> mapping.getVariant().getId())
                .distinct()
                .toList();
        Map<UUID, List<InventoryItem>> inventoryByVariantId = inventoryItemRepository.findByVariantIdIn(variantIds)
                .stream()
                .filter(item -> matchesDefaultWarehouse(item, defaultWarehouseId))
                .collect(Collectors.groupingBy(item -> item.getVariant().getId()));

        int syncedSkuCount = 0;
        Set<UUID> affectedProductIds = new HashSet<>();
        List<String> skuPayloads = new ArrayList<>();
        List<ChannelProductVariant> batchMappings = new ArrayList<>();

        for (ChannelProductVariant mapping : mappings) {
            List<InventoryItem> inventoryItems = inventoryByVariantId.getOrDefault(mapping.getVariant().getId(), List.of());
            if (inventoryItems.isEmpty()) {
                continue;
            }

            List<InventoryItem> scopedItems = scopeInventoryItems(
                    inventoryItems,
                    changedScope,
                    changedSince,
                    defaultWarehouseId,
                    defaultWarehouseCode
            );
            if (scopedItems.isEmpty()) {
                log.info(
                        "[LazadaStockSync] Skip variant without scoped inventory channelId={} variantId={} sellerSku={}",
                        channelId,
                        mapping.getVariant().getId(),
                        firstNonBlank(mapping.getExternalSku(), mapping.getVariant().getSku())
                );
                continue;
            }

            if (mapping.getVariant().getProduct() != null && mapping.getVariant().getProduct().getId() != null) {
                affectedProductIds.add(mapping.getVariant().getProduct().getId());
            }
            logSkuChange(mapping, scopedItems, defaultWarehouseId, defaultWarehouseCode);
            skuPayloads.add(buildSkuPayload(mapping, scopedItems, defaultWarehouseId, defaultWarehouseCode));
            batchMappings.add(mapping);

            if (skuPayloads.size() == SKU_BATCH_SIZE) {
                syncedSkuCount += sendBatch(credential, skuPayloads, batchMappings);
                skuPayloads.clear();
                batchMappings.clear();
            }
        }

        if (!skuPayloads.isEmpty()) {
            syncedSkuCount += sendBatch(credential, skuPayloads, batchMappings);
        }

        return new LazadaInventorySyncResult(
                affectedProductIds.size(),
                syncedSkuCount,
                changedSince == null ? 0 : changedScope.warehouseIds().size()
        );
    }

    private ChangedScope resolveChangedScope(OffsetDateTime changedSince,
                                             OffsetDateTime changedUntil,
                                             Collection<UUID> productChangedVariantIds) {
        if (changedSince == null) {
            return new ChangedScope(
                    productChangedVariantIds == null ? Set.of() : new HashSet<>(productChangedVariantIds),
                    Set.of()
            );
        }

        Set<UUID> variantIds = new HashSet<>();
        Set<UUID> warehouseIds = new HashSet<>();
        if (productChangedVariantIds != null) {
            variantIds.addAll(productChangedVariantIds);
        }

        OffsetDateTime effectiveChangedUntil = changedUntil == null ? OffsetDateTime.now() : changedUntil;
        variantIds.addAll(stockReceiveRepository.findChangedConfirmedVariantIdsBetween(changedSince, effectiveChangedUntil));
        variantIds.addAll(inventoryIssueRepository.findChangedAppliedVariantIdsBetween(changedSince, effectiveChangedUntil));
        warehouseIds.addAll(stockReceiveRepository.findChangedConfirmedWarehouseIdsBetween(changedSince, effectiveChangedUntil));
        warehouseIds.addAll(inventoryIssueRepository.findChangedAppliedWarehouseIdsBetween(changedSince, effectiveChangedUntil));

        log.info(
                "[LazadaStockSync] Resolved stock documents changedSince={} changedUntil={} variantCount={} warehouseCount={}",
                changedSince,
                effectiveChangedUntil,
                variantIds.size(),
                warehouseIds.size()
        );
        return new ChangedScope(variantIds, warehouseIds);
    }

    private List<InventoryItem> scopeInventoryItems(List<InventoryItem> inventoryItems,
                                                    ChangedScope changedScope,
                                                    OffsetDateTime changedSince,
                                                    UUID defaultWarehouseId,
                                                    String defaultWarehouseCode) {
        if (changedSince == null) {
            return inventoryItems;
        }

        List<InventoryItem> warehouseScopedItems = inventoryItems.stream()
                .filter(item -> item.getWarehouse() != null)
                .filter(item -> changedScope.warehouseIds().contains(item.getWarehouse().getId()))
                .toList();

        boolean hasWarehouseCode = warehouseScopedItems.stream()
                .anyMatch(item -> resolveWarehouseCode(item.getWarehouse(), defaultWarehouseId, defaultWarehouseCode) != null);
        log.info(
                "[LazadaStockSync] Scoped inventory items changedSince={} originalItemCount={} scopedItemCount={} hasWarehouseCode={}",
                changedSince,
                inventoryItems.size(),
                warehouseScopedItems.size(),
                hasWarehouseCode
        );
        return hasWarehouseCode ? warehouseScopedItems : inventoryItems;
    }

    private int sendBatch(ChannelCredential credential,
                          List<String> skuPayloads,
                          List<ChannelProductVariant> batchMappings) {
        String payload = "<Request><Product><Skus>"
                + String.join("", skuPayloads)
                + "</Skus></Product></Request>";

        Map<String, String> params = new HashMap<>();
        params.put("payload", payload);
        log.info(
                "[LazadaStockSync] Calling Lazada API api={} batchSkuCount={} itemSkuPairs={} payloadLength={}",
                API_PATH,
                batchMappings.size(),
                batchMappings.stream()
                        .map(mapping -> mapping.getChannelProduct().getExternalProductId()
                                + "/"
                                + mapping.getExternalVariantId()
                                + "/"
                                + firstNonBlank(mapping.getExternalSku(), mapping.getVariant().getSku()))
                        .toList(),
                payload.length()
        );

        String response = lazadaApiClient.executePost(
                credential.getChannel().getId(), API_PATH, params);
        ensureSuccess(response);
        log.info(
                "[LazadaStockSync] Lazada API success api={} batchSkuCount={} responseLength={}",
                API_PATH,
                batchMappings.size(),
                response == null ? 0 : response.length()
        );

        OffsetDateTime syncedAt = OffsetDateTime.now();
        for (ChannelProductVariant mapping : batchMappings) {
            mapping.setSyncStatus(SyncStatus.SYNCED);
            mapping.setLastSyncedAt(syncedAt);
            channelProductVariantRepository.save(mapping);
        }

        return batchMappings.size();
    }

    private void logSkuChange(ChannelProductVariant mapping,
                              List<InventoryItem> inventoryItems,
                              UUID defaultWarehouseId,
                              String defaultWarehouseCode) {
        String productName = mapping.getVariant().getProduct() == null
                ? null
                : mapping.getVariant().getProduct().getName();
        String sellerSku = firstNonBlank(mapping.getExternalSku(), mapping.getVariant().getSku());
        log.info(
                "[LazadaStockSync] Prepare SKU itemId={} skuId={} sellerSku={} localVariantId={} product={} warehouses={}",
                mapping.getChannelProduct().getExternalProductId(),
                mapping.getExternalVariantId(),
                sellerSku,
                mapping.getVariant().getId(),
                productName,
                inventoryItems.stream()
                        .map(item -> Map.of(
                                "warehouseId", String.valueOf(item.getWarehouse().getId()),
                                "warehouseName", item.getWarehouse().getName(),
                                "warehouseCode", String.valueOf(resolveWarehouseCode(item.getWarehouse(), defaultWarehouseId, defaultWarehouseCode)),
                                "availableQuantity", availableQuantity(item)
                        ))
                        .toList()
        );
    }

    private String buildSkuPayload(ChannelProductVariant mapping,
                                   List<InventoryItem> inventoryItems,
                                   UUID defaultWarehouseId,
                                   String defaultWarehouseCode) {
        StringBuilder payload = new StringBuilder()
                .append("<Sku>")
                .append("<ItemId>").append(escapeXml(mapping.getChannelProduct().getExternalProductId())).append("</ItemId>")
                .append("<SkuId>").append(escapeXml(mapping.getExternalVariantId())).append("</SkuId>");

        String sellerSku = firstNonBlank(mapping.getExternalSku(), mapping.getVariant().getSku());
        if (sellerSku != null) {
            payload.append("<SellerSku>").append(escapeXml(sellerSku)).append("</SellerSku>");
        }

        int targetSellableQuantity = marketplaceStockQuantityResolver.maxAvailableQuantityForSkuGroup(mapping);
        List<WarehouseQuantity> warehouseQuantities = inventoryItems.stream()
                .map(item -> new WarehouseQuantity(resolveWarehouseCode(item.getWarehouse(), defaultWarehouseId, defaultWarehouseCode), availableQuantity(item)))
                .filter(item -> item.warehouseCode() != null && !item.warehouseCode().isBlank())
                .toList();

        if (warehouseQuantities.isEmpty()) {
            payload.append("<SellableQuantity>").append(Math.max(targetSellableQuantity, 0)).append("</SellableQuantity>");
        } else {
            payload.append("<MultiWarehouseInventories>");
            String primaryWarehouseCode = firstWarehouseCode(warehouseQuantities, defaultWarehouseCode);
            for (WarehouseQuantity warehouseQuantity : warehouseQuantities) {
                int sellableQuantity = warehouseQuantity.warehouseCode().equals(primaryWarehouseCode)
                        ? targetSellableQuantity
                        : 0;
                payload.append("<MultiWarehouseInventory>")
                        .append("<WarehouseCode>").append(escapeXml(warehouseQuantity.warehouseCode())).append("</WarehouseCode>")
                        .append("<SellableQuantity>").append(Math.max(sellableQuantity, 0)).append("</SellableQuantity>")
                        .append("</MultiWarehouseInventory>");
            }
            payload.append("</MultiWarehouseInventories>");
        }

        return payload.append("</Sku>").toString();
    }

    private String firstWarehouseCode(List<WarehouseQuantity> warehouseQuantities, String defaultWarehouseCode) {
        if (defaultWarehouseCode != null && !defaultWarehouseCode.isBlank()) {
            for (WarehouseQuantity warehouseQuantity : warehouseQuantities) {
                if (defaultWarehouseCode.equals(warehouseQuantity.warehouseCode())) {
                    return defaultWarehouseCode;
                }
            }
        }
        return warehouseQuantities.get(0).warehouseCode();
    }

    private void ensureSuccess(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            String code = root.path("code").asText("");
            if (!code.isBlank() && !"0".equals(code)) {
                String message = firstNonBlank(
                        root.path("message").asText(null),
                        root.path("msg").asText(null),
                        root.path("error_msg").asText(null),
                        response
                );
                throw new IllegalStateException("Lazada API " + API_PATH + " loi: " + message);
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Khong doc duoc response cap nhat ton Lazada.", e);
        }
    }

    private String resolveWarehouseCode(Warehouse warehouse, UUID defaultWarehouseId, String defaultWarehouseCode) {
        if (defaultWarehouseCode != null
                && defaultWarehouseId != null
                && warehouse != null
                && defaultWarehouseId.equals(warehouse.getId())) {
            return defaultWarehouseCode;
        }

        String address = warehouse == null ? null : warehouse.getAddress();
        if (address == null) {
            return null;
        }

        int markerIndex = address.indexOf(WAREHOUSE_CODE_MARKER);
        if (markerIndex < 0) {
            return null;
        }

        String code = address.substring(markerIndex + WAREHOUSE_CODE_MARKER.length()).trim();
        int separatorIndex = code.indexOf(']');
        if (separatorIndex >= 0) {
            code = code.substring(0, separatorIndex).trim();
        }
        return code.isBlank() ? null : code;
    }

    private UUID resolveDefaultWarehouseId(ChannelCredential credential) {
        Object warehouseId = credential.getChannel() == null || credential.getChannel().getMetadata() == null
                ? null
                : credential.getChannel().getMetadata().get("defaultWarehouseId");
        if (warehouseId == null || warehouseId.toString().isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(warehouseId.toString());
        } catch (IllegalArgumentException ignored) {
            log.warn(
                    "[LazadaStockSync] Ignore invalid defaultWarehouseId={} channelId={}",
                    warehouseId,
                    credential.getChannel() == null ? null : credential.getChannel().getId()
            );
            return null;
        }
    }

    private String resolveDefaultWarehouseCode(ChannelCredential credential, UUID defaultWarehouseId) {
        if (defaultWarehouseId == null
                || credential.getChannel() == null
                || credential.getChannel().getMetadata() == null) {
            return null;
        }

        Object warehouseCode = credential.getChannel().getMetadata().get("lazadaWarehouseCode");
        if (warehouseCode == null || warehouseCode.toString().isBlank()) {
            return null;
        }
        return warehouseCode.toString().trim();
    }

    private boolean matchesDefaultWarehouse(InventoryItem item, UUID defaultWarehouseId) {
        if (defaultWarehouseId == null) {
            return true;
        }
        return item.getWarehouse() != null && defaultWarehouseId.equals(item.getWarehouse().getId());
    }

    private int availableQuantity(InventoryItem inventoryItem) {
        if (inventoryItem.getAvailableQuantity() != null) {
            return Math.max(inventoryItem.getAvailableQuantity(), 0);
        }
        return 0;
    }

    private Long tokenExpiresAt(ChannelCredential credential) {
        return credential.getTokenExpiresAt() == null ? null : credential.getTokenExpiresAt().toEpochSecond();
    }

    private String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private record WarehouseQuantity(String warehouseCode, int sellableQuantity) {
    }

    private record ChangedScope(Set<UUID> variantIds, Set<UUID> warehouseIds) {
    }
}
