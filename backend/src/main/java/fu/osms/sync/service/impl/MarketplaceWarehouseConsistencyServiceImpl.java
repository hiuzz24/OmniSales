package fu.osms.sync.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelCredential;
import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.inventory.repository.WarehouseRepository;
import fu.osms.sync.lazada.service.LazadaAuthorizedApiClient;
import fu.osms.sync.service.MarketplaceWarehouseConsistencyService;
import fu.osms.sync.shopify.ShopifyApiClient;
import fu.osms.sync.tiktok.TikTokAuthorizedApiClient;
import fu.osms.sync.tiktok.util.TikTokWarehouseAddressFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketplaceWarehouseConsistencyServiceImpl implements MarketplaceWarehouseConsistencyService {

    private static final List<PlatformType> SUPPORTED_PLATFORMS = List.of(
            PlatformType.SHOPIFY,
            PlatformType.LAZADA,
            PlatformType.TIKTOK);
    private static final String SHARED_WAREHOUSE_DISPLAY_NAME = "Kho mặc định đa sàn";
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}]+");

    private final ChannelRepository channelRepository;
    private final ChannelCredentialRepository credentialRepository;
    private final WarehouseRepository warehouseRepository;
    private final ShopifyApiClient shopifyApiClient;
    private final LazadaAuthorizedApiClient lazadaApiClient;
    private final TikTokAuthorizedApiClient tikTokApiClient;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public Warehouse resolveMasterWarehouse() {
        Warehouse warehouse = resolveSharedWarehouse(null);
        alignSupportedChannelWarehouseMetadata(warehouse);
        return warehouse;
    }

    @Override
    @Transactional
    public Warehouse resolveAndValidatePrimaryWarehouse(Channel channel) {
        if (channel == null || channel.getId() == null || !SUPPORTED_PLATFORMS.contains(channel.getPlatform())) {
            return resolveSharedWarehouse(null);
        }

        validateConnectedPrimaryWarehouses();
        RemotePrimaryWarehouse remoteWarehouse = fetchPrimaryWarehouse(channel);
        Warehouse warehouse = resolveSharedWarehouse(remoteWarehouse.firstAddressLine());
        persistChannelWarehouseMetadata(channel, warehouse, remoteWarehouse);
        alignSupportedChannelWarehouseMetadata(warehouse);
        return warehouse;
    }

    @Override
    @Transactional
    public void validateConnectedPrimaryWarehouses() {
        List<RemotePrimaryWarehouse> warehouses = connectedMarketplaceChannels().stream()
                .map(this::fetchPrimaryWarehouse)
                .filter(warehouse -> hasText(warehouse.firstAddressLine()))
                .toList();

        if (warehouses.size() <= 1) {
            return;
        }

        RemotePrimaryWarehouse baseline = warehouses.get(0);
        String normalizedBaseline = normalizeAddressLine(baseline.firstAddressLine());
        List<RemotePrimaryWarehouse> mismatches = warehouses.stream()
                .filter(warehouse -> !Objects.equals(normalizedBaseline,
                        normalizeAddressLine(warehouse.firstAddressLine())))
                .toList();

        if (!mismatches.isEmpty()) {
            String details = warehouses.stream()
                    .map(warehouse -> warehouse.platform() + " \"" + warehouse.channelName() + "\": "
                            + warehouse.firstAddressLine())
                    .toList()
                    .toString();
            throw new AppException(
                    ErrorCode.INVALID_REQUEST,
                    "Kho chính của các sàn chưa cùng địa chỉ. Vui lòng cấu hình cùng dòng địa chỉ đầu tiên trước khi đồng bộ tồn kho: "
                            + details);
        }
    }

    private List<Channel> connectedMarketplaceChannels() {
        List<Channel> result = new ArrayList<>();
        for (Channel channel : channelRepository.findByDeletedAtIsNull()) {
            if (!SUPPORTED_PLATFORMS.contains(channel.getPlatform())
                    || !Boolean.TRUE.equals(channel.getSyncEnabled())) {
                continue;
            }
            Optional<ChannelCredential> credential = credentialRepository
                    .findByChannelIdAndConnectionState(channel.getId(), "CONNECTED");
            if (credential.isPresent() && hasText(credential.get().getAccessToken())) {
                result.add(channel);
            }
        }
        return result;
    }

    private RemotePrimaryWarehouse fetchPrimaryWarehouse(Channel channel) {
        return switch (channel.getPlatform()) {
            case SHOPIFY -> fetchShopifyPrimaryWarehouse(channel);
            case LAZADA -> fetchLazadaPrimaryWarehouse(channel);
            case TIKTOK -> fetchTikTokPrimaryWarehouse(channel);
            default -> throw new AppException(ErrorCode.INVALID_REQUEST, "Sàn chưa hỗ trợ kiểm tra kho chính.");
        };
    }

    @SuppressWarnings("unchecked")
    private RemotePrimaryWarehouse fetchShopifyPrimaryWarehouse(Channel channel) {
        ChannelCredential credential = connectedCredential(channel);
        Map<String, Object> response = shopifyApiClient.executeGraphQl(
                extractShopDomain(channel),
                credential.getAccessToken(),
                """
                        query {
                          locations(first: 50, includeInactive: true) {
                            nodes {
                              id
                              name
                              isActive
                              isPrimary
                              address {
                                formatted
                                address1
                                address2
                                city
                                province
                                zip
                                country
                              }
                            }
                          }
                        }
                        """,
                Map.of());
        ensureNoGraphQlErrors(response);

        Map<String, Object> data = map(response.get("data"));
        Map<String, Object> locations = map(data.get("locations"));
        List<Map<String, Object>> nodes = listOfMaps(locations.get("nodes"));
        Map<String, Object> selected = nodes.stream()
                .filter(node -> booleanValue(node.get("isPrimary")) && booleanValue(node.get("isActive")))
                .findFirst()
                .orElseGet(() -> nodes.stream()
                        .filter(node -> booleanValue(node.get("isActive")))
                        .findFirst()
                        .orElseThrow(() -> new AppException(
                                ErrorCode.INVALID_REQUEST,
                                "Shopify chưa có location active để đồng bộ tồn kho.")));

        Map<String, Object> address = map(selected.get("address"));
        String firstLine = firstFormattedAddressLine(address);
        return new RemotePrimaryWarehouse(
                channel.getPlatform(),
                channel.getId(),
                channel.getDisplayName(),
                numericId(stringValue(selected.get("id"))),
                stringValue(selected.get("name")),
                firstLine,
                "shopifyLocationId");
    }

    private RemotePrimaryWarehouse fetchLazadaPrimaryWarehouse(Channel channel) {
        JsonNode root = fetchLazadaWarehouseDetail(channel);
        JsonNode selected = findFirstObjectWithAny(root,
                "detail_address",
                "detailAddress",
                "warehouse_code",
                "warehouseCode").orElseThrow(
                        () -> new AppException(
                                ErrorCode.INVALID_REQUEST,
                                "Lazada không trả về kho chính từ /rc/warehouse/detail/get."));

        String address = firstText(selected,
                "detail_address",
                "detailAddress",
                "address",
                "warehouse_address",
                "warehouseAddress");
        String code = firstText(selected, "warehouse_code", "warehouseCode", "code", "id", "warehouse_id");
        return new RemotePrimaryWarehouse(
                channel.getPlatform(),
                channel.getId(),
                channel.getDisplayName(),
                code,
                firstText(selected, "name", "warehouse_name", "warehouseName"),
                firstAddressLine(address),
                "lazadaWarehouseCode");
    }

    private JsonNode fetchLazadaWarehouseDetail(Channel channel) {
        try {
            String response = lazadaApiClient.executeGet(channel.getId(), "/rc/warehouse/detail/get", Map.of());
            JsonNode root = objectMapper.readTree(response);
            ensureLazadaSuccess(root, "/rc/warehouse/detail/get");
            return root;
        } catch (Exception detailError) {
            log.warn("[WarehouseConsistency] Lazada detail/get failed channelId={}, fallback to warehouse/get: {}",
                    channel.getId(), detailError.getMessage());
            try {
                String response = lazadaApiClient.executeGet(channel.getId(), "/rc/warehouse/get", Map.of());
                JsonNode root = objectMapper.readTree(response);
                ensureLazadaSuccess(root, "/rc/warehouse/get");
                return root;
            } catch (Exception fallbackError) {
                throw new AppException(
                        ErrorCode.INVALID_REQUEST,
                        "Không lấy được kho Lazada để kiểm tra địa chỉ: " + fallbackError.getMessage());
            }
        }
    }

    private RemotePrimaryWarehouse fetchTikTokPrimaryWarehouse(Channel channel) {
        String shopCipher = requireMetadataText(channel, "shopCipher", "shop_cipher", "cipher");
        Map<String, Object> response = tikTokApiClient.getWarehouses(channel.getId(), shopCipher);
        List<Map<String, Object>> warehouses = listOfMaps(map(response.get("data")).get("warehouses"));
        Map<String, Object> selected = warehouses.stream()
                .filter(warehouse -> booleanValue(warehouse.get("is_default")))
                .findFirst()
                .orElseThrow(() -> new AppException(
                        ErrorCode.INVALID_REQUEST,
                        "TikTok Shop chưa có warehouse is_default=true."));

        String address = firstNonBlank(
                stringValue(selected.get("full_address")),
                stringValue(map(selected.get("address")).get("full_address")),
                stringValue(map(selected.get("address")).get("fullAddress")),
                formatTikTokAddress(map(selected.get("address")), stringValue(selected.get("id"))));
        return new RemotePrimaryWarehouse(
                channel.getPlatform(),
                channel.getId(),
                channel.getDisplayName(),
                stringValue(selected.get("id")),
                stringValue(selected.get("name")),
                firstAddressLine(address),
                "tiktokWarehouseId");
    }

    private Warehouse resolveSharedWarehouse(String remoteAddress) {
        Map<UUID, Long> configuredWarehouseUsage = connectedMarketplaceChannels().stream()
                .map(Channel::getMetadata)
                .map(metadata -> optionalText(metadata, "defaultWarehouseId"))
                .filter(this::hasText)
                .map(this::parseUuidOrNull)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        List<UUID> configuredWarehouseIds = configuredWarehouseUsage.entrySet().stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(entry -> entry.getKey().toString()))
                .map(Map.Entry::getKey)
                .toList();
        for (UUID configuredWarehouseId : configuredWarehouseIds) {
            Optional<Warehouse> configuredWarehouse = warehouseRepository.findById(configuredWarehouseId)
                    .filter(warehouse -> warehouse.getDeletedAt() == null
                            && Boolean.TRUE.equals(warehouse.getIsActive()));
            if (configuredWarehouse.isPresent()) {
                return ensureWarehouseAddress(configuredWarehouse.get(), remoteAddress);
            }
        }

        List<Warehouse> activeWarehouses = warehouseRepository.findByDeletedAtIsNull().stream()
                .filter(warehouse -> Boolean.TRUE.equals(warehouse.getIsActive()))
                .toList();
        if (activeWarehouses.size() == 1) {
            return ensureWarehouseAddress(activeWarehouses.get(0), remoteAddress);
        }

        Warehouse warehouse = warehouseRepository.findFirstByNameAndDeletedAtIsNull(SHARED_WAREHOUSE_DISPLAY_NAME)
                .orElseGet(() -> Warehouse.builder()
                        .name(SHARED_WAREHOUSE_DISPLAY_NAME)
                        .isActive(true)
                        .build());
        if (!hasText(warehouse.getAddress()) && hasText(remoteAddress)) {
            warehouse.setAddress(remoteAddress);
        }
        warehouse.setIsActive(true);
        return warehouseRepository.save(warehouse);
    }

    private Warehouse ensureWarehouseAddress(Warehouse warehouse, String remoteAddress) {
        if (warehouse == null || !hasText(remoteAddress) || hasText(warehouse.getAddress())) {
            return warehouse;
        }
        warehouse.setAddress(remoteAddress);
        return warehouseRepository.save(warehouse);
    }

    private void alignSupportedChannelWarehouseMetadata(Warehouse warehouse) {
        if (warehouse == null || warehouse.getId() == null) {
            return;
        }
        String canonicalWarehouseId = warehouse.getId().toString();
        for (Channel channel : channelRepository.findByDeletedAtIsNull()) {
            if (!SUPPORTED_PLATFORMS.contains(channel.getPlatform())
                    || !Boolean.TRUE.equals(channel.getSyncEnabled())) {
                continue;
            }
            Map<String, Object> metadata = channel.getMetadata() == null
                    ? new HashMap<>()
                    : new HashMap<>(channel.getMetadata());
            if (canonicalWarehouseId.equals(optionalText(metadata, "defaultWarehouseId"))) {
                continue;
            }
            metadata.put("defaultWarehouseId", canonicalWarehouseId);
            channel.setMetadata(metadata);
            channelRepository.save(channel);
        }
    }

    private void persistChannelWarehouseMetadata(Channel channel,
            Warehouse warehouse,
            RemotePrimaryWarehouse remoteWarehouse) {
        Map<String, Object> metadata = channel.getMetadata() == null
                ? new HashMap<>()
                : new HashMap<>(channel.getMetadata());
        metadata.put("defaultWarehouseId", warehouse.getId().toString());
        if (hasText(remoteWarehouse.externalWarehouseId())) {
            metadata.put(remoteWarehouse.externalMetadataKey(), remoteWarehouse.externalWarehouseId());
        }
        if (hasText(remoteWarehouse.firstAddressLine())) {
            metadata.put(remoteWarehouse.platform().name().toLowerCase(Locale.ROOT) + "PrimaryWarehouseAddress",
                    remoteWarehouse.firstAddressLine());
        }
        channel.setMetadata(metadata);
        channelRepository.save(channel);
    }

    private ChannelCredential connectedCredential(Channel channel) {
        return credentialRepository.findByChannelIdAndConnectionState(channel.getId(), "CONNECTED")
                .orElseThrow(() -> new AppException(
                        ErrorCode.CHANNEL_NOT_CONNECTED,
                        "Kênh " + channel.getDisplayName() + " chưa kết nối."));
    }

    private String extractShopDomain(Channel channel) {
        String shopDomain = optionalText(channel.getMetadata(), "shopDomain", "shop");
        return hasText(shopDomain) ? shopDomain : channel.getDisplayName();
    }

    private void ensureNoGraphQlErrors(Map<String, Object> response) {
        if (response.get("errors") != null) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Shopify GraphQL lỗi: " + response.get("errors"));
        }
    }

    private void ensureLazadaSuccess(JsonNode root, String apiPath) {
        String code = root.path("code").asText("");
        if (!code.isBlank() && !"0".equals(code)) {
            String message = firstNonBlank(
                    root.path("message").asText(null),
                    root.path("msg").asText(null),
                    root.path("error_msg").asText(null));
            throw new AppException(ErrorCode.INVALID_REQUEST, "Lazada API " + apiPath + " lỗi: " + message);
        }
    }

    private Optional<JsonNode> findFirstObjectWithAny(JsonNode node, String... fieldNames) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Optional.empty();
        }
        if (node.isObject()) {
            for (String fieldName : fieldNames) {
                if (node.has(fieldName)) {
                    return Optional.of(node);
                }
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                Optional<JsonNode> found = findFirstObjectWithAny(fields.next().getValue(), fieldNames);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                Optional<JsonNode> found = findFirstObjectWithAny(child, fieldNames);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    private String firstFormattedAddressLine(Map<String, Object> address) {
        Object formatted = address.get("formatted");
        if (formatted instanceof List<?> lines) {
            return lines.stream()
                    .map(this::stringValue)
                    .filter(this::hasText)
                    .findFirst()
                    .orElse(null);
        }
        return firstAddressLine(firstNonBlank(
                stringValue(formatted),
                stringValue(address.get("address1"))));
    }

    private String formatTikTokAddress(Map<String, Object> address, String warehouseId) {
        if (address == null || address.isEmpty()) {
            return null;
        }
        String formatted = TikTokWarehouseAddressFormatter.format(address, warehouseId);
        return formatted != null && formatted.startsWith("TikTok warehouse ") ? null : formatted;
    }

    private String normalizeAddressLine(String value) {
        if (!hasText(value)) {
            return "";
        }
        String stripped = DIACRITICS.matcher(Normalizer.normalize(value.trim(), Normalizer.Form.NFD)).replaceAll("");
        return NON_ALNUM.matcher(stripped.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }

    private String firstAddressLine(String value) {
        if (!hasText(value)) {
            return null;
        }
        String[] parts = value.split("\\R|,");
        return parts.length == 0 ? value.trim() : parts[0].trim();
    }

    private String requireMetadataText(Channel channel, String... keys) {
        String value = optionalText(channel.getMetadata(), keys);
        if (!hasText(value)) {
            throw new AppException(ErrorCode.INVALID_REQUEST,
                    "Kênh " + channel.getDisplayName() + " thiếu metadata " + List.of(keys));
        }
        return value;
    }

    private String optionalText(Map<String, Object> metadata, String... keys) {
        if (metadata == null) {
            return null;
        }
        for (String key : keys) {
            Object value = metadata.get(key);
            if (value != null && hasText(value.toString())) {
                return value.toString();
            }
        }
        return null;
    }

    private UUID parseUuidOrNull(String value) {
        try {
            return UUID.fromString(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String numericId(String gid) {
        if (!hasText(gid)) {
            return gid;
        }
        int index = gid.lastIndexOf('/');
        return index >= 0 ? gid.substring(index + 1) : gid;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> source ? (Map<String, Object>) source : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(item -> item instanceof Map<?, ?>)
                .map(item -> (Map<String, Object>) item)
                .toList();
    }

    private String firstText(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull() && hasText(value.asText())) {
                return value.asText();
            }
        }
        var fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            for (String name : names) {
                if (field.getKey().equalsIgnoreCase(name)) {
                    JsonNode value = field.getValue();
                    if (value != null && !value.isNull() && hasText(value.asText())) {
                        return value.asText();
                    }
                }
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(value.toString());
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank() && !"null".equalsIgnoreCase(value);
    }

    private record RemotePrimaryWarehouse(
            PlatformType platform,
            UUID channelId,
            String channelName,
            String externalWarehouseId,
            String externalWarehouseName,
            String firstAddressLine,
            String externalMetadataKey) {
    }
}
