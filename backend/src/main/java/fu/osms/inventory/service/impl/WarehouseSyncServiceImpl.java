package fu.osms.inventory.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
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
import fu.osms.inventory.dto.response.WarehouseMarketplaceSyncResult;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.inventory.repository.WarehouseRepository;
import fu.osms.inventory.service.WarehouseService;
import fu.osms.inventory.service.WarehouseSyncService;
import fu.osms.sync.lazada.service.LazadaAuthorizedApiClient;
import fu.osms.sync.shopify.ShopifyApiClient;
import fu.osms.sync.tiktok.TikTokAuthorizedApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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

    private final WarehouseRepository warehouseRepository;
    private final WarehouseService warehouseService;
    private final ChannelRepository channelRepository;
    private final ChannelCredentialRepository credentialRepository;
    private final ShopifyApiClient shopifyApiClient;
    private final LazadaAuthorizedApiClient lazadaApiClient;
    private final TikTokAuthorizedApiClient tikTokApiClient;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public WarehouseMarketplaceSyncResult syncToMarketplaces(UUID warehouseId,
                                                              WarehouseMarketplaceSyncRequest request) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy kho hàng."));

        // Update local warehouse first
        warehouseService.update(warehouseId, WarehouseRequest.builder()
                .name(request.getName())
                .address(request.getAddress())
                .isActive(warehouse.getIsActive())
                .build());

        List<WarehouseMarketplaceSyncResult.ChannelSyncStatus> results = new ArrayList<>();
        for (Channel channel : connectedChannels()) {
            results.add(syncChannel(channel, warehouse, request));
        }

        boolean allOk = results.stream().allMatch(WarehouseMarketplaceSyncResult.ChannelSyncStatus::isSuccess);
        return WarehouseMarketplaceSyncResult.builder()
                .allSucceeded(allOk)
                .channels(results)
                .build();
    }

    private WarehouseMarketplaceSyncResult.ChannelSyncStatus syncChannel(
            Channel channel, Warehouse warehouse, WarehouseMarketplaceSyncRequest request) {
        try {
            String syncedAddress = switch (channel.getPlatform()) {
                case SHOPIFY -> syncShopify(channel, request);
                case LAZADA  -> syncLazada(channel, request);
                case TIKTOK  -> syncTikTok(channel, request);
                default -> throw new UnsupportedOperationException("Sàn chưa hỗ trợ.");
            };
            return WarehouseMarketplaceSyncResult.ChannelSyncStatus.builder()
                    .channelId(channel.getId().toString())
                    .channelName(channel.getDisplayName())
                    .platform(channel.getPlatform().name())
                    .success(true)
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
                    .error(rootMessage(e))
                    .build();
        }
    }

    // ── Shopify: locationEdit ─────────────────────────────────────────────
    //
    // FIX: LocationEditInput does NOT have an "id" field.
    // The location ID is passed as a separate top-level argument to the mutation,
    // and the input object only contains name, address, fulfillsOnlineOrders.
    //
    // Correct signature:
    //   locationEdit(id: ID!, input: LocationEditInput!) { ... }
    //
    // Ref: https://shopify.dev/docs/api/admin-graphql/latest/mutations/locationEdit
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
        // GID format: gid://shopify/Location/{numericId}
        String gid = locationId.startsWith("gid://") ? locationId
                : "gid://shopify/Location/" + locationId;

        // Build LocationEditAddressInput — no "id" field here
        Map<String, Object> addressInput = new LinkedHashMap<>();
        addressInput.put("address1", req.getAddress());
        addressInput.put("phone", hasText(req.getPhone()) ? req.getPhone() : null);
        if (hasText(req.getCity())) addressInput.put("city", req.getCity());
        if (hasText(req.getZip())) addressInput.put("zip", req.getZip());
        addressInput.put("countryCode", hasText(req.getCountryCode()) ? req.getCountryCode() : "VN");
        if (hasText(req.getProvince())) addressInput.put("provinceCode", req.getProvince());

        // LocationEditInput: name, address, fulfillsOnlineOrders — NO id field
        Map<String, Object> locationInput = new LinkedHashMap<>();
        locationInput.put("name", req.getName());
        locationInput.put("address", addressInput);

        // id is a separate mutation argument, not inside input
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
        try {
            if (response.get("data") instanceof Map<?, ?> dataMap
                    && dataMap.get("locationEdit") instanceof Map<?, ?> editMap
                    && editMap.get("userErrors") instanceof List<?> errorList
                    && !errorList.isEmpty()) {
                throw new IllegalStateException("Shopify userErrors: " + errorList.get(0));
            }
        } catch (IllegalStateException rethrow) {
            throw rethrow;
        } catch (Exception ignored) { /* non-critical */ }
        return req.getAddress();
    }

    // ── Lazada: warehouse info is read-only via Open API ──────────────────
    //
    // Lazada Open API does NOT expose a public endpoint to update warehouse
    // name/address/contact for sellers. The /rc/warehouse/update path does not
    // exist in the published API catalogue.
    //
    // We save the info locally and return a clear "not supported" message
    // rather than failing silently or throwing a cryptic 404.
    private String syncLazada(Channel channel, WarehouseMarketplaceSyncRequest req) {
        log.info("[WarehouseSync] Lazada does not support warehouse update via Open API. " +
                 "Saved locally only. channelId={}", channel.getId());
        // Intentionally not calling any Lazada API.
        // Return a special marker so the caller knows this was a no-op.
        throw new IllegalStateException(
                "Lazada Open API không hỗ trợ cập nhật thông tin kho qua API. " +
                "Vui lòng cập nhật thủ công tại Lazada Seller Center.");
    }

    // ── TikTok: warehouse update is not available via Open API ───────────
    //
    // TikTok Shop Open API does not expose a public endpoint to update
    // warehouse contact/address information for regular sellers.
    // The /api/logistics/warehouses/{id} PUT path returned 404 with code 36009009
    // "Invalid path. The specified path does not match any available endpoint."
    //
    // We report this limitation clearly rather than sending a broken request.
    private String syncTikTok(Channel channel, WarehouseMarketplaceSyncRequest req) {
        log.info("[WarehouseSync] TikTok does not support warehouse update via Open API. " +
                 "Saved locally only. channelId={}", channel.getId());
        throw new IllegalStateException(
                "TikTok Shop Open API không hỗ trợ cập nhật thông tin kho qua API. " +
                "Vui lòng cập nhật thủ công tại TikTok Seller Center.");
    }

    // ── Helpers ───────────────────────────────────────────────────────────

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
