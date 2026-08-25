package fu.osms.inventory.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelCredential;
import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.inventory.dto.request.WarehouseMarketplaceSyncRequest;
import fu.osms.inventory.dto.request.WarehouseRequest;
import fu.osms.inventory.dto.response.WarehouseAddressComparisonResult;
import fu.osms.inventory.dto.response.WarehouseMarketplaceSyncResult;
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.repository.WarehouseRepository;
import fu.osms.inventory.service.WarehouseService;
import fu.osms.inventory.service.WarehouseSyncService;
import fu.osms.sync.lazada.service.LazadaAuthorizedApiClient;
import fu.osms.sync.service.MarketplaceWarehouseConsistencyService;
import fu.osms.sync.shopify.ShopifyApiClient;
import fu.osms.sync.tiktok.TikTokAuthorizedApiClient;
import fu.osms.sync.util.WarehouseAddressUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseSyncServiceImpl implements WarehouseSyncService {

    private static final List<PlatformType> SUPPORTED = List.of(
            PlatformType.SHOPIFY, PlatformType.LAZADA, PlatformType.TIKTOK);
    private static final String SHARED_WAREHOUSE_DISPLAY_NAME = "Kho mặc định đa sàn";
    private static final List<PlatformType> ADDRESS_PRIORITY = List.of(
            PlatformType.TIKTOK, PlatformType.LAZADA, PlatformType.SHOPIFY);

    private final WarehouseRepository warehouseRepository;
    private final WarehouseService warehouseService;
    private final InventoryItemRepository inventoryItemRepository;
    private final ChannelRepository channelRepository;
    private final ChannelCredentialRepository credentialRepository;
    private final ShopifyApiClient shopifyApiClient;
    private final LazadaAuthorizedApiClient lazadaApiClient;
    private final TikTokAuthorizedApiClient tikTokApiClient;
    private final ObjectMapper objectMapper;
    private final MarketplaceWarehouseConsistencyService marketplaceWarehouseConsistencyService;

    @Override
    @Transactional
    public WarehouseMarketplaceSyncResult syncToMarketplaces(UUID warehouseId,
                                                              WarehouseMarketplaceSyncRequest request) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy kho hàng."));

        warehouseService.update(warehouseId, WarehouseRequest.builder()
                .name(request.getName())
                .address(request.getAddress())
                .isActive(warehouse.getIsActive())
                .build());

        List<WarehouseMarketplaceSyncResult.ChannelSyncStatus> results = new ArrayList<>();
        for (Channel channel : connectedChannels()) {
            results.add(syncChannel(channel, warehouse, request));
        }

        boolean allOk = results.stream()
                .filter(r -> !r.isSavedLocallyOnly())
                .allMatch(WarehouseMarketplaceSyncResult.ChannelSyncStatus::isSuccess);
        return WarehouseMarketplaceSyncResult.builder()
                .allSucceeded(allOk)
                .channels(results)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public WarehouseAddressComparisonResult comparePlatformAddresses() {
        Warehouse masterWarehouse = marketplaceWarehouseConsistencyService.resolveMasterWarehouse();
        String currentAddress = masterWarehouse.getAddress();

        List<WarehouseAddressComparisonResult.PlatformAddress> platformAddresses =
                marketplaceWarehouseConsistencyService.fetchConnectedPlatformAddresses();

        if (platformAddresses.isEmpty()) {
            return WarehouseAddressComparisonResult.builder()
                    .status("NO_CHANNELS")
                    .currentWarehouseId(masterWarehouse.getId().toString())
                    .currentWarehouseAddress(currentAddress)
                    .platformAddresses(platformAddresses)
                    .build();
        }

        String firstPlatformAddress = platformAddresses.get(0).getAddress();
        boolean allSame = platformAddresses.stream()
                .allMatch(pa -> WarehouseAddressUtils.isAddressSimilar(pa.getAddress(), firstPlatformAddress));

        if (!allSame) {
            return WarehouseAddressComparisonResult.builder()
                    .status("ALL_DIFFERENT")
                    .currentWarehouseId(masterWarehouse.getId().toString())
                    .currentWarehouseAddress(currentAddress)
                    .platformAddresses(platformAddresses)
                    .build();
        }

        boolean allMatchCurrent = platformAddresses.stream()
                .allMatch(pa -> WarehouseAddressUtils.isAddressSimilar(pa.getAddress(), currentAddress));

        if (allMatchCurrent) {
            return WarehouseAddressComparisonResult.builder()
                    .status("SAME_AS_CURRENT")
                    .currentWarehouseId(masterWarehouse.getId().toString())
                    .currentWarehouseAddress(currentAddress)
                    .platformAddresses(platformAddresses)
                    .build();
        }

        return WarehouseAddressComparisonResult.builder()
                .status("ALL_SAME")
                .currentWarehouseId(masterWarehouse.getId().toString())
                .currentWarehouseAddress(currentAddress)
                .platformAddresses(platformAddresses)
                .build();
    }

    @Override
    @Transactional
    public void applyAddressSync(boolean confirm) {
        if (!confirm) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Bạn chưa xác nhận đồng bộ địa chỉ kho.");
        }

        WarehouseAddressComparisonResult comparison = comparePlatformAddresses();

        if ("NO_CHANNELS".equals(comparison.getStatus())) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Không có sàn nào đang kết nối.");
        }
        if ("ALL_DIFFERENT".equals(comparison.getStatus())) {
            throw new AppException(ErrorCode.INVALID_REQUEST,
                    "Các địa chỉ kho hàng trên các sàn không đồng nhất. Vui lòng cập nhật thủ công.");
        }
        if ("SAME_AS_CURRENT".equals(comparison.getStatus())) {
            return;
        }
        if (!"ALL_SAME".equals(comparison.getStatus())) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Trạng thái không hợp lệ để áp dụng.");
        }

        String syncedAddress = pickBestAddress(comparison.getPlatformAddresses());
        String cleanedAddress = WarehouseAddressUtils.stripPostalCodes(syncedAddress);

        Warehouse oldWarehouse = warehouseRepository
                .findFirstByNameAndDeletedAtIsNullOrderByIdAsc(SHARED_WAREHOUSE_DISPLAY_NAME)
                .filter(w -> Boolean.TRUE.equals(w.getIsActive()))
                .orElse(null);
        if (oldWarehouse == null) {
            UUID oldWarehouseId = UUID.fromString(comparison.getCurrentWarehouseId());
            oldWarehouse = warehouseRepository.findById(oldWarehouseId)
                    .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND,
                            "Không tìm thấy kho hàng."));
        }

        Warehouse newWarehouse = Warehouse.builder()
                .name(SHARED_WAREHOUSE_DISPLAY_NAME)
                .address(cleanedAddress)
                .isActive(true)
                .build();
        Warehouse savedWarehouse = warehouseRepository.save(newWarehouse);

        if (!oldWarehouse.getId().equals(savedWarehouse.getId())) {
            oldWarehouse.setIsActive(false);
            warehouseRepository.save(oldWarehouse);
        }

        List<Warehouse> duplicates = warehouseRepository
                .findByNameAndDeletedAtIsNull(SHARED_WAREHOUSE_DISPLAY_NAME);
        for (Warehouse w : duplicates) {
            if (!w.getId().equals(savedWarehouse.getId()) && !w.getId().equals(oldWarehouse.getId())) {
                w.setIsActive(false);
                warehouseRepository.save(w);
            }
        }

        migrateInventoryItems(oldWarehouse, savedWarehouse);

        String newWarehouseId = savedWarehouse.getId().toString();
        for (Channel channel : channelRepository.findByDeletedAtIsNull()) {
            if (!SUPPORTED.contains(channel.getPlatform())
                    || !Boolean.TRUE.equals(channel.getSyncEnabled())) {
                continue;
            }
            Map<String, Object> metadata = channel.getMetadata() == null
                    ? new HashMap<>()
                    : new HashMap<>(channel.getMetadata());
            metadata.put("defaultWarehouseId", newWarehouseId);
            channel.setMetadata(metadata);
            channelRepository.save(channel);
        }

        log.info("[WarehouseSync] Address sync applied: oldWarehouse={}, newWarehouse={}, address='{}'",
                oldWarehouse.getId(), savedWarehouse.getId(), cleanedAddress);
    }

    private void migrateInventoryItems(Warehouse oldWarehouse, Warehouse newWarehouse) {
        List<InventoryItem> oldItems = inventoryItemRepository.findByWarehouseId(oldWarehouse.getId());
        if (oldItems.isEmpty()) return;

        int moved = 0;
        int merged = 0;
        List<InventoryItem> toDelete = new ArrayList<>();

        for (InventoryItem oldItem : oldItems) {
            Optional<InventoryItem> existingOpt = inventoryItemRepository
                    .findByWarehouseIdAndVariantId(newWarehouse.getId(), oldItem.getVariant().getId());

            if (existingOpt.isPresent()) {
                InventoryItem existing = existingOpt.get();
                int totalQty = existing.getQuantityOnHand() + oldItem.getQuantityOnHand();
                int totalReserved = existing.getReservedQuantity() + oldItem.getReservedQuantity();
                existing.setQuantityOnHand(totalQty);
                existing.setReservedQuantity(Math.min(totalReserved, totalQty));
                if (totalQty > 0) {
                    BigDecimal existingCost = existing.getAverageCost() != null
                            ? existing.getAverageCost() : BigDecimal.ZERO;
                    BigDecimal oldCost = oldItem.getAverageCost() != null
                            ? oldItem.getAverageCost() : BigDecimal.ZERO;
                    existing.setAverageCost(existingCost
                            .multiply(BigDecimal.valueOf(existing.getQuantityOnHand()))
                            .add(oldCost.multiply(BigDecimal.valueOf(oldItem.getQuantityOnHand())))
                            .divide(BigDecimal.valueOf(totalQty), 2, java.math.RoundingMode.HALF_UP));
                }
                inventoryItemRepository.save(existing);
                toDelete.add(oldItem);
                merged++;
            } else {
                oldItem.setWarehouse(newWarehouse);
                inventoryItemRepository.save(oldItem);
                moved++;
            }
        }

        inventoryItemRepository.deleteAll(toDelete);

        log.info("[WarehouseSync] Inventory migration: {} moved, {} merged from warehouse {} to {}",
                moved, merged, oldWarehouse.getId(), newWarehouse.getId());
    }

    private String pickBestAddress(List<WarehouseAddressComparisonResult.PlatformAddress> platformAddresses) {
        for (PlatformType platform : ADDRESS_PRIORITY) {
            for (WarehouseAddressComparisonResult.PlatformAddress pa : platformAddresses) {
                if (platform.name().equals(pa.getPlatform()) && pa.getAddress() != null
                        && !pa.getAddress().isBlank()) {
                    return pa.getAddress();
                }
            }
        }
        return platformAddresses.stream()
                .map(WarehouseAddressComparisonResult.PlatformAddress::getAddress)
                .filter(a -> a != null && !a.isBlank())
                .findFirst().orElse("");
    }

    private WarehouseMarketplaceSyncResult.ChannelSyncStatus syncChannel(
            Channel channel, Warehouse warehouse, WarehouseMarketplaceSyncRequest request) {
        boolean savedLocallyOnly = false;
        try {
            String syncedAddress = switch (channel.getPlatform()) {
                case SHOPIFY -> syncShopify(channel, request);
                case LAZADA -> {
                    savedLocallyOnly = true;
                    yield syncUnsupportedPlatform(channel, request);
                }
                case TIKTOK -> {
                    savedLocallyOnly = true;
                    yield syncUnsupportedPlatform(channel, request);
                }
                default -> throw new UnsupportedOperationException("Sàn chưa hỗ trợ.");
            };
            return WarehouseMarketplaceSyncResult.ChannelSyncStatus.builder()
                    .channelId(channel.getId().toString())
                    .channelName(channel.getDisplayName())
                    .platform(channel.getPlatform().name())
                    .success(true)
                    .savedLocallyOnly(savedLocallyOnly)
                    .syncedName(request.getName())
                    .syncedAddress(syncedAddress)
                    .syncedContactName(request.getContactName())
                    .syncedPhone(request.getPhone())
                    .build();
        } catch (Exception e) {
            log.warn("[WarehouseSync] Failed channel={} platform={}: {}",
                    channel.getDisplayName(), channel.getPlatform(), e.getMessage());
            return WarehouseMarketplaceSyncResult.ChannelSyncStatus.builder()
                    .channelId(channel.getId().toString())
                    .channelName(channel.getDisplayName())
                    .platform(channel.getPlatform().name())
                    .success(false)
                    .savedLocallyOnly(savedLocallyOnly)
                    .error(rootMessage(e))
                    .build();
        }
    }

    private String syncShopify(Channel channel, WarehouseMarketplaceSyncRequest req) {
        String shopDomain = metaText(channel, "shopDomain", "shop");
        if (!hasText(shopDomain)) {
            throw new IllegalStateException("Thiếu shopDomain trong metadata kênh Shopify.");
        }
        ChannelCredential cred = connectedCredential(channel);
        String locationId = metaText(channel, "shopifyLocationId");
        if (!hasText(locationId)) {
            throw new IllegalStateException(
                    "Thiếu shopifyLocationId — vui lòng đồng bộ kho từ Shopify trước.");
        }
        String gid = locationId.startsWith("gid://") ? locationId
                : "gid://shopify/Location/" + locationId;

        Map<String, Object> addressInput = new LinkedHashMap<>();
        addressInput.put("address1", req.getAddress());
        addressInput.put("phone", hasText(req.getPhone()) ? req.getPhone() : null);
        if (hasText(req.getCity())) addressInput.put("city", req.getCity());
        if (hasText(req.getZip())) addressInput.put("zip", req.getZip());
        addressInput.put("countryCode", hasText(req.getCountryCode()) ? req.getCountryCode() : "VN");
        if (hasText(req.getProvince())) addressInput.put("provinceCode", req.getProvince());

        Map<String, Object> locationInput = new LinkedHashMap<>();
        locationInput.put("name", req.getName());
        locationInput.put("address", addressInput);

        String mutation = """
                mutation locationEdit($id: ID!, $input: LocationEditInput!) {
                  locationEdit(id: $id, input: $input) {
                    location {
                      id
                      name
                      address { address1 phone }
                    }
                    userErrors { field message code }
                  }
                }
                """;
        Map<String, Object> variables = Map.of("id", gid, "input", locationInput);

        Map<String, Object> response = shopifyApiClient.executeGraphQl(
                shopDomain, cred.getAccessToken(), mutation, variables);

        if (response.get("errors") != null) {
            throw new IllegalStateException("Shopify GraphQL errors: " + response.get("errors"));
        }
        if (response.get("data") instanceof Map<?, ?> dataMap
                && dataMap.get("locationEdit") instanceof Map<?, ?> editMap
                && editMap.get("userErrors") instanceof List<?> errorList
                && !errorList.isEmpty()) {
            throw new IllegalStateException("Shopify userErrors: " + errorList.get(0));
        }
        return req.getAddress();
    }

    /**
     * Lazada/TikTok Open API does not support warehouse update.
     * Save locally only — do not throw.
     */
    private String syncUnsupportedPlatform(Channel channel, WarehouseMarketplaceSyncRequest req) {
        log.info("[WarehouseSync] {} does not support warehouse update via Open API. Saved locally only. channelId={}",
                channel.getPlatform(), channel.getId());
        return req.getAddress();
    }

    private List<Channel> connectedChannels() {
        List<Channel> result = new ArrayList<>();
        for (Channel channel : channelRepository.findByDeletedAtIsNull()) {
            if (!SUPPORTED.contains(channel.getPlatform())
                    || !Boolean.TRUE.equals(channel.getSyncEnabled())) continue;
            Optional<ChannelCredential> cred = credentialRepository
                    .findByChannelIdAndConnectionState(channel.getId(), "CONNECTED");
            if (cred.isPresent() && hasText(cred.get().getAccessToken())) result.add(channel);
        }
        return result;
    }

    private ChannelCredential connectedCredential(Channel channel) {
        return credentialRepository.findByChannelIdAndConnectionState(channel.getId(), "CONNECTED")
                .orElseThrow(() -> new AppException(ErrorCode.CHANNEL_NOT_CONNECTED,
                        "Kênh " + channel.getDisplayName() + " chưa kết nối."));
    }

    private String metaText(Channel channel, String... keys) {
        if (channel.getMetadata() == null) return null;
        for (String key : keys) {
            Object val = channel.getMetadata().get(key);
            if (val != null && hasText(val.toString())) return val.toString();
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
