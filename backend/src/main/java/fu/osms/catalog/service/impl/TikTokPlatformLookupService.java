package fu.osms.catalog.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.catalog.dto.response.PlatformAttributeOptionResponse;
import fu.osms.catalog.dto.response.PlatformAttributeResponse;
import fu.osms.catalog.dto.response.PlatformCategoryNodeResponse;
import fu.osms.catalog.dto.response.PlatformBrandResponse;
import fu.osms.catalog.dto.response.PlatformBrandPageResponse;
import fu.osms.catalog.service.PlatformLookupService;
import fu.osms.catalog.dto.request.CategorySuggestionRequest;
import fu.osms.catalog.dto.response.PlatformCategorySuggestionResponse;
import fu.osms.channel.entity.ChannelCredential;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.sync.tiktok.TikTokApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;

@Slf4j
@Service
@RequiredArgsConstructor
public class TikTokPlatformLookupService implements PlatformLookupService {

    private static final String DEFAULT_CATEGORY_VERSION = "v1";

    private final TikTokApiClient tikTokApiClient;
    private final ChannelCredentialRepository credentialRepository;
    private final ChannelRepository channelRepository;
    private final ObjectMapper objectMapper;
    private final CacheManager cacheManager;

    @Value("${tiktok.fashion-root-category-ids:}")
    private String fashionRootCategoryIds;

    @Override
    public PlatformType getPlatform() {
        return PlatformType.TIKTOK;
    }

    @Override
    // MANUAL_CATEGORY_BROWSER_FALLBACK: suggestion-first no longer calls this automatically.
    public List<PlatformCategoryNodeResponse> getCategories(UUID channelId, String parentId, String keyword, String categoryVersion) {
        List<PlatformCategoryNodeResponse> categories = categories(channelId, version(categoryVersion));
        if (keyword != null && !keyword.isBlank()) {
            String normalized = keyword.trim().toLowerCase(Locale.ROOT);
            return categories.stream()
                    .filter(node -> node.getName() != null && node.getName().toLowerCase(Locale.ROOT).contains(normalized))
                    .toList();
        }
        if (parentId != null && !parentId.isBlank()) {
            return categories.stream().filter(node -> parentId.equals(node.getParentId())).toList();
        }
        Set<String> roots = configuredFashionRoots();
        if (!roots.isEmpty()) {
            return categories.stream().filter(node -> roots.contains(node.getId())).toList();
        }
        return categories.stream().filter(node -> "0".equals(node.getParentId()) || node.getParentId() == null).toList();
    }

    @Override
    public List<PlatformAttributeResponse> getAttributes(UUID channelId, String categoryId, String categoryVersion) {
        if (categoryId == null || categoryId.isBlank()) return Collections.emptyList();
        String version = version(categoryVersion);
        Cache cache = cacheManager.getCache("tiktokCategoryAttributes");
        String key = channelId + ":" + version + ":" + categoryId;
        return cached(cache, key, () -> loadAttributes(channelId, categoryId, version));
    }

    @Override
    public boolean isLeafCategory(UUID channelId, String categoryId, String categoryVersion) {
        return categories(channelId, version(categoryVersion)).stream()
                .anyMatch(node -> categoryId != null && categoryId.equals(node.getId())
                        && Boolean.TRUE.equals(node.getLeaf()) && Boolean.TRUE.equals(node.getAvailable()));
    }

    @Override
    public PlatformBrandPageResponse getBrands(UUID channelId, String categoryId, String categoryVersion,
                                                String keyword, int page, int size, String pageToken) {
        if (categoryId == null || categoryId.isBlank()) {
            return PlatformBrandPageResponse.builder().items(List.of()).page(page).size(size).totalElements(0).totalPages(0).build();
        }
        String version = version(categoryVersion);
        return loadBrandPage(channelId, categoryId, version, keyword, page, size, pageToken);
    }

    @Override
    public List<PlatformCategorySuggestionResponse> suggestCategories(CategorySuggestionRequest request) {
        requireSuggestionInput(request);
        String version = version(request.getCategoryVersion());
        String imageUri = cached(cacheManager.getCache("tiktokSuggestionImageUris"),
                request.getChannelId() + ":" + request.getPrimaryImageUrl(),
                () -> uploadSuggestionImage(request.getPrimaryImageUrl(), accessToken(request.getChannelId())));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("product_title", request.getTitle().trim());
        if (request.getDescription() != null && !request.getDescription().isBlank()) body.put("description", request.getDescription());
        body.put("images", List.of(Map.of("uri", imageUri)));
        body.put("category_version", version);
        String rawBody = writeJson(body);
        log.info("[TikTokCategorySuggestion] channelId={} categoryVersion={}", request.getChannelId(), version);
        String response = tikTokApiClient.executePost("/product/202309/categories/recommend",
                Map.of("shop_cipher", shopCipher(request.getChannelId())), rawBody, accessToken(request.getChannelId()));
        JsonNode root = readTree(response);
        ensureSuccess(root, "/product/202309/categories/recommend");
        List<PlatformCategorySuggestionResponse> suggestions = mapLeafCategorySuggestion(root.path("data"), inputHash(request));
        log.info("[TikTokCategorySuggestion] categoryVersion={} suggestions={}", version, suggestions);
        return suggestions;
    }

    @Override
    public void clearCache(UUID channelId) {
        String prefix = channelId + ":";
        evictByPrefix("tiktokCategories", prefix);
        evictByPrefix("tiktokCategoryAttributes", prefix);
        evictByPrefix("tiktokSuggestionImageUris", prefix);
    }

    private List<PlatformCategoryNodeResponse> categories(UUID channelId, String version) {
        Cache cache = cacheManager.getCache("tiktokCategories");
        return cached(cache, channelId + ":" + version, () -> loadCategories(channelId, version));
    }

    private List<PlatformCategoryNodeResponse> loadCategories(UUID channelId, String categoryVersion) {
        String response = tikTokApiClient.executeGet(
                "/product/202309/categories",
                Map.of("category_version", categoryVersion, "locale", "vi-VN", "shop_cipher", shopCipher(channelId)),
                accessToken(channelId)
        );
        JsonNode root = readTree(response);
        ensureSuccess(root, "/product/202309/categories");
        List<PlatformCategoryNodeResponse> categories = new ArrayList<>();
        JsonNode rawCategories = root.path("data").path("categories");
        if (rawCategories.isArray()) {
            rawCategories.forEach(node -> categories.add(PlatformCategoryNodeResponse.builder()
                    .id(text(node, "id"))
                    .parentId(text(node, "parent_id"))
                    .name(text(node, "local_name", "name"))
                    .leaf(node.path("is_leaf").asBoolean(false))
                    .available(hasAvailablePermission(permissionStatuses(node)))
                    .build()));
        }
        return categories;
    }

    private List<PlatformAttributeResponse> loadAttributes(UUID channelId, String categoryId, String categoryVersion) {
        log.info("[TikTokCategoryAttributes] channelId={} categoryId={} categoryVersion={}",
                channelId, categoryId, categoryVersion);
        String response = tikTokApiClient.executeGet(
                "/product/202309/categories/" + categoryId + "/attributes",
                Map.of("category_version", categoryVersion, "locale", "vi-VN", "shop_cipher", shopCipher(channelId)),
                accessToken(channelId)
        );
        JsonNode root = readTree(response);
        ensureSuccess(root, "/product/202309/categories/{categoryId}/attributes");
        JsonNode rawAttributes = root.path("data").path("attributes");
        List<PlatformAttributeResponse> attributes = new ArrayList<>();
        if (rawAttributes.isArray()) {
            rawAttributes.forEach(attribute -> attributes.add(toAttribute(attribute)));
        }
        log.info("[tiktok attribute:]{}",attributes);
        return attributes;
    }

    private PlatformBrandPageResponse loadBrandPage(UUID channelId, String categoryId, String categoryVersion,
                                                    String keyword, int page, int size, String pageToken) {
        Map<String, String> params = new HashMap<>();
        params.put("category_id", categoryId);
        params.put("category_version", categoryVersion);
        params.put("page_size", String.valueOf(size));
        params.put("shop_cipher", shopCipher(channelId));
        if (keyword != null && !keyword.isBlank()) params.put("brand_name", keyword.trim());
        if (pageToken != null && !pageToken.isBlank()) params.put("page_token", pageToken);
        List<PlatformBrandResponse> brands = new ArrayList<>();
        String response = tikTokApiClient.executeGet("/product/202309/brands", params, accessToken(channelId));
        JsonNode root = readTree(response);
        ensureSuccess(root, "/product/202309/brands");
        JsonNode rawBrands = root.path("data").path("brands");
        if (rawBrands.isArray()) rawBrands.forEach(brand -> {
            String id = text(brand, "id");
            String name = text(brand, "name");
            if (id != null && name != null) brands.add(PlatformBrandResponse.builder().id(id).name(name).build());
        });
        return PlatformBrandPageResponse.builder().items(brands).page(page).size(size)
                .totalElements(0).totalPages(0)
                .nextPageToken(text(root.path("data"), "next_page_token"))
                .hasNext(text(root.path("data"), "next_page_token") != null)
                .build();
    }

    private String uploadSuggestionImage(String imageUrl, String accessToken) {
        JsonNode root = readTree(tikTokApiClient.uploadProductImage(imageUrl, "MAIN_IMAGE", accessToken));
        ensureSuccess(root, "/product/202309/images/upload");
        String uri = text(root.path("data"), "uri");
        if (uri == null || uri.isBlank()) throw new IllegalStateException("TikTok suggestion image upload is missing URI");
        return uri;
    }

    private void requireSuggestionInput(CategorySuggestionRequest request) {
        if (request == null || request.getChannelId() == null || request.getTitle() == null || request.getTitle().isBlank()
                || request.getPrimaryImageUrl() == null || request.getPrimaryImageUrl().isBlank()) {
            throw new IllegalArgumentException("Category suggestion requires channel, title and primary image");
        }
    }

    private List<PlatformCategorySuggestionResponse> mapLeafCategorySuggestion(JsonNode data, String inputHash) {
        String leafCategoryId = text(data, "leaf_category_id");
        if (leafCategoryId == null) {
            log.warn("[TikTokCategorySuggestion] Response is missing data.leaf_category_id");
            return List.of();
        }

        JsonNode categories = data.path("categories");
        if (!categories.isArray()) {
            log.warn("[TikTokCategorySuggestion] Response is missing data.categories for leafCategoryId={}", leafCategoryId);
            return List.of();
        }

        for (JsonNode category : categories) {
            if (!leafCategoryId.equals(text(category, "id"))) continue;
            String name = text(category, "local_name", "name");
            if (name == null) {
                log.warn("[TikTokCategorySuggestion] Leaf category {} is missing a name", leafCategoryId);
                return List.of();
            }
            boolean selectable = category.path("is_leaf").asBoolean(false)
                    && hasAvailablePermission(permissionStatuses(category));
            return List.of(PlatformCategorySuggestionResponse.builder()
                    .inputHash(inputHash)
                    .categoryId(leafCategoryId)
                    .categoryName(name)
                    .selectable(selectable)
                    .disabledReason(selectable ? null : "Shop chua duoc cap quyen dung danh muc nay")
                    .build());
        }

        log.warn("[TikTokCategorySuggestion] Leaf category {} was not found in data.categories", leafCategoryId);
        return List.of();
        /* Legacy recursive mapper. It could expose non-leaf path nodes as selectable suggestions.
        if (node == null || node.isMissingNode() || node.isNull()) return;
        String id = text(node, "category_id", "categoryId", "id");
        String name = text(node, "category_name", "categoryName", "name", "local_name");
        if (id != null && name != null) {
            boolean selectable = hasAvailablePermission(permissionStatuses(node));
            result.putIfAbsent(id, PlatformCategorySuggestionResponse.builder().inputHash(inputHash).categoryId(id)
                    .categoryName(name).selectable(selectable)
                    .disabledReason(selectable ? null : "Shop chưa được cấp quyền dùng danh mục này").build());
        }
        if (node.isObject()) node.elements().forEachRemaining(child -> collectSuggestions(child, inputHash, result));
        if (node.isArray()) node.forEach(child -> collectSuggestions(child, inputHash, result));
        */
    }

    private String writeJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("Cannot serialize TikTok category suggestion", e); }
    }

    private String inputHash(CategorySuggestionRequest request) {
        return Integer.toHexString((request.getTitle() + "|" + String.valueOf(request.getDescription()) + "|" + request.getPrimaryImageUrl()).hashCode());
    }

    @SuppressWarnings("unchecked")
    private void evictByPrefix(String cacheName, String prefix) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) return;
        Object nativeCache = cache.getNativeCache();
        if (nativeCache instanceof com.github.benmanes.caffeine.cache.Cache<?, ?> caffeineCache) {
            ((com.github.benmanes.caffeine.cache.Cache<Object, Object>) caffeineCache).asMap().keySet()
                    .removeIf(key -> String.valueOf(key).startsWith(prefix));
            return;
        }
        cache.clear();
    }

    private PlatformAttributeResponse toAttribute(JsonNode attribute) {
        List<PlatformAttributeOptionResponse> options = new ArrayList<>();
        JsonNode rawOptions = attribute.path("values");
        if (!rawOptions.isArray()) rawOptions = attribute.path("options");
        if (rawOptions.isArray()) {
            rawOptions.forEach(option -> options.add(PlatformAttributeOptionResponse.builder()
                    .id(text(option, "id"))
                    .name(text(option, "name", "local_name"))
                    .build()));
        }
        return PlatformAttributeResponse.builder()
                .id(text(attribute, "id", "attribute_id"))
                .name(text(attribute, "name", "attribute_name", "id"))
                .label(text(attribute, "local_name", "name", "attribute_name"))
                .inputType(text(attribute, "input_type", "type"))
                .required(attribute.path("is_required").asBoolean(false))
                .saleProperty(attribute.path("is_sale_property").asBoolean(false)
                        || attribute.path("is_sale_prop").asBoolean(false))
                .options(options)
                .build();
    }

    private String accessToken(UUID channelId) {
        return credentialRepository.findByChannelIdAndConnectionState(channelId, "CONNECTED")
                .filter(value -> value.getAccessToken() != null && !value.getAccessToken().isBlank())
                .map(ChannelCredential::getAccessToken)
                .orElseThrow(() -> new IllegalStateException("TikTok channel is not connected"));
    }

    private String shopCipher(UUID channelId) {
        return channelRepository.findById(channelId)
                .map(channel -> channel.getMetadata() == null ? null : channel.getMetadata().get("shopCipher"))
                .map(String::valueOf)
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new IllegalStateException("TikTok shop cipher is missing. Reconnect the channel."));
    }

    private <T> T cached(Cache cache, Object key, Callable<T> loader) {
        try {
            return cache.get(key, loader);
        } catch (Cache.ValueRetrievalException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("TikTok lookup failed", cause);
        }
    }

    private JsonNode readTree(String response) {
        try {
            return objectMapper.readTree(response);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot parse TikTok lookup response", e);
        }
    }

    private void ensureSuccess(JsonNode root, String apiPath) {
        if (root.path("code").asInt(-1) != 0) {
            throw new IllegalStateException(apiPath + " failed: " + root.path("message").asText("Unknown error"));
        }
    }

    private boolean hasAvailablePermission(JsonNode statuses) {
        if (statuses == null || statuses.isMissingNode() || statuses.isNull()) return true;
        if (statuses.isTextual()) return "AVAILABLE".equalsIgnoreCase(statuses.asText());
        if (!statuses.isArray()) return true;
        for (JsonNode status : statuses) {
            String value = status.isObject() ? text(status, "permission_status", "status") : status.asText();
            if ("AVAILABLE".equalsIgnoreCase(value)) return true;
        }
        return false;
    }

    private JsonNode permissionStatuses(JsonNode node) {
        JsonNode statuses = node.path("permission_statuses");
        return statuses.isMissingNode() ? node.path("permission_status") : statuses;
    }

    private String version(String categoryVersion) {
        return categoryVersion == null || categoryVersion.isBlank() ? DEFAULT_CATEGORY_VERSION : categoryVersion;
    }

    private Set<String> configuredFashionRoots() {
        if (fashionRootCategoryIds == null || fashionRootCategoryIds.isBlank()) return Set.of();
        return new HashSet<>(Arrays.stream(fashionRootCategoryIds.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList());
    }

    private String text(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.path(name);
            if (!value.isMissingNode() && !value.isNull() && !value.asText().isBlank()) return value.asText();
        }
        return null;
    }
}
