package fu.osms.catalog.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.catalog.dto.response.PlatformAttributeOptionResponse;
import fu.osms.catalog.dto.response.PlatformAttributeResponse;
import fu.osms.catalog.dto.response.PlatformBrandResponse;
import fu.osms.catalog.dto.response.PlatformBrandPageResponse;
import fu.osms.catalog.dto.response.PlatformCategoryNodeResponse;
import fu.osms.catalog.dto.response.PlatformCategorySuggestionResponse;
import fu.osms.catalog.dto.request.CategorySuggestionRequest;
import fu.osms.catalog.service.PlatformLookupService;
import fu.osms.channel.entity.ChannelCredential;
import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.sync.lazada.service.LazadaApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;

@Service
@Slf4j
@RequiredArgsConstructor
public class LazadaPlatformLookupService implements PlatformLookupService {

    private final LazadaApiClient lazadaApiClient;
    private final ChannelCredentialRepository credentialRepository;
    private final ObjectMapper objectMapper;
    private final CacheManager cacheManager;

    @Value("${lazada.fashion-root-category-ids:}")
    private String fashionRootCategoryIds;

    @Override
    public PlatformType getPlatform() {
        return PlatformType.LAZADA;
    }

    @Override
    // MANUAL_CATEGORY_BROWSER_FALLBACK: suggestion-first no longer calls this automatically.
    public List<PlatformCategoryNodeResponse> getCategories(UUID channelId, String parentId, String keyword, String ignoredVersion) {
        List<PlatformCategoryNodeResponse> tree = categoryTree(channelId);
        if (keyword != null && !keyword.isBlank()) {
            String normalized = keyword.trim().toLowerCase(Locale.ROOT);
            return tree.stream()
                    .filter(node -> node.getName() != null && node.getName().toLowerCase(Locale.ROOT).contains(normalized))
                    .toList();
        }
        if (parentId != null && !parentId.isBlank()) {
            return tree.stream().filter(node -> parentId.equals(node.getParentId())).toList();
        }
        Set<String> roots = configuredFashionRoots();
        if (!roots.isEmpty()) {
            return tree.stream().filter(node -> roots.contains(node.getId())).toList();
        }
        return tree.stream().filter(node -> node.getParentId() == null || node.getParentId().isBlank() || "0".equals(node.getParentId())).toList();
    }

    @Override
    public List<PlatformAttributeResponse> getAttributes(UUID channelId, String categoryId, String ignoredVersion) {
        if (categoryId == null || categoryId.isBlank()) {
            return Collections.emptyList();
        }
        Cache cache = cacheManager.getCache("lazadaCategoryAttributes");
        String key = channelId + ":" + categoryId;
        return cached(cache, key, () -> loadAttributes(channelId, categoryId));
    }

    @Override
    public PlatformBrandPageResponse getBrands(UUID channelId, String ignoredCategoryId, String ignoredCategoryVersion,
                                                String keyword, int page, int size, String ignoredPageToken) {
        return loadBrandPage(channelId, keyword, page, size);
    }

    @Override
    public List<PlatformCategorySuggestionResponse> suggestCategories(CategorySuggestionRequest request) {
        requireSuggestionInput(request);
        String response = lazadaApiClient.executeGet("/product/category/suggestion/get", Map.of(
                "product_name", request.getTitle().trim(),
                "image_url", request.getPrimaryImageUrl().trim()
        ), accessToken(request.getChannelId()), tokenExpiresAt(request.getChannelId()));
        JsonNode root = readTree(response);
        ensureSuccess(root, "/product/category/suggestion/get");
        Map<String, PlatformCategorySuggestionResponse> result = new LinkedHashMap<>();
        collectSuggestions(root.path("data"), inputHash(request), result);
        return new ArrayList<>(result.values());
    }

    @Override
    public boolean isLeafCategory(UUID channelId, String categoryId, String ignoredVersion) {
        return categoryTree(channelId).stream()
                .anyMatch(node -> categoryId != null && categoryId.equals(node.getId()) && Boolean.TRUE.equals(node.getLeaf()));
    }

    @Override
    public void clearCache(UUID channelId) {
        cacheManager.getCache("lazadaCategoryTree").evict(channelId);
        evictByPrefix("lazadaCategoryAttributes", channelId + ":");
    }

    private List<PlatformCategoryNodeResponse> categoryTree(UUID channelId) {
        return cached(cacheManager.getCache("lazadaCategoryTree"), channelId, () -> loadCategoryTree(channelId));
    }

    private List<PlatformCategoryNodeResponse> loadCategoryTree(UUID channelId) {
        String response = lazadaApiClient.executeGet("/category/tree/get", Map.of(), accessToken(channelId), tokenExpiresAt(channelId));
        JsonNode root = readTree(response);
        ensureSuccess(root, "/category/tree/get");
        List<PlatformCategoryNodeResponse> nodes = new ArrayList<>();
        collectCategories(root.path("data"), null, nodes);
        return nodes;
    }

    private List<PlatformAttributeResponse> loadAttributes(UUID channelId, String categoryId) {
        JsonNode root = executeAttributesRequest(channelId, categoryId, false);
        JsonNode data = root.path("data");
        List<PlatformAttributeResponse> attributes = new ArrayList<>();
        if (data.isArray()) {
            data.forEach(attribute -> attributes.add(toAttribute(attribute)));
        }
        return attributes;
    }

    private PlatformBrandPageResponse loadBrandPage(UUID channelId, String keyword, int page, int size) {
        int startRow = page * size;
        Map<String, String> params = new java.util.HashMap<>();
        params.put("startRow", String.valueOf(startRow));
        params.put("pageSize", String.valueOf(size));
        if (keyword != null && !keyword.isBlank()) {
            params.put("brand_name", keyword.trim());
        }
        String response = lazadaApiClient.executeGet("/category/brands/query", params,
                accessToken(channelId), tokenExpiresAt(channelId));
        JsonNode root = readTree(response);
        ensureSuccess(root, "/category/brands/query");
        JsonNode items = brandItems(root.path("data"));
        List<PlatformBrandResponse> brands = new ArrayList<>();
        if (items.isArray()) {
            for (JsonNode brand : items) {
                String id = text(brand, "local_brand_id", "brand_id", "id");
                String name = text(brand, "local_brand_name", "name", "brand_name");
                if (id != null && name != null) brands.add(PlatformBrandResponse.builder().id(id).name(name).build());
            }
        }
        return PlatformBrandPageResponse.builder().items(brands).page(page).size(size)
                .totalElements(0).totalPages(0).hasNext(brands.size() >= size).build();
    }

    private void requireSuggestionInput(CategorySuggestionRequest request) {
        if (request == null || request.getChannelId() == null || request.getTitle() == null || request.getTitle().isBlank()
                || request.getPrimaryImageUrl() == null || request.getPrimaryImageUrl().isBlank()) {
            throw new IllegalArgumentException("Category suggestion requires channel, title and primary image");
        }
    }

    private void collectSuggestions(JsonNode node, String inputHash, Map<String, PlatformCategorySuggestionResponse> result) {
        if (node == null || node.isMissingNode() || node.isNull()) return;
        String id = text(node, "category_id", "categoryId", "id");
        String name = text(node, "category_name", "categoryName", "name");
        if (id != null && name != null) result.putIfAbsent(id, PlatformCategorySuggestionResponse.builder()
                .inputHash(inputHash).categoryId(id).categoryName(name).selectable(true).build());
        if (node.isObject()) node.elements().forEachRemaining(child -> collectSuggestions(child, inputHash, result));
        if (node.isArray()) node.forEach(child -> collectSuggestions(child, inputHash, result));
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

    private JsonNode executeAttributesRequest(UUID channelId, String categoryId, boolean retried) {
        String response = lazadaApiClient.executeGet(
                "/category/attributes/get",
                Map.of("primary_category_id", categoryId),
                accessToken(channelId),
                tokenExpiresAt(channelId)
        );
        JsonNode root = readTree(response);
        if ("0".equals(root.path("code").asText())) {
            return root;
        }
        String message = root.path("message").asText("Unknown error");
        if (!retried && message.toLowerCase(Locale.ROOT).contains("access frequency exceeds the limit")) {
            try {
                Thread.sleep(1100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting to retry Lazada category attributes", e);
            }
            return executeAttributesRequest(channelId, categoryId, true);
        }
        throw new IllegalStateException("/category/attributes/get failed: " + message);
    }

    private JsonNode brandItems(JsonNode data) {
        if (data.isArray()) {
            return data;
        }
        for (String field : List.of("module", "brand_list", "brands", "items", "data")) {
            JsonNode value = data.path(field);
            if (value.isArray()) {
                return value;
            }
        }
        return data;
    }

    private PlatformAttributeResponse toAttribute(JsonNode attribute) {
        List<PlatformAttributeOptionResponse> options = new ArrayList<>();
        JsonNode rawOptions = attribute.path("options");
        if (rawOptions.isArray()) {
            rawOptions.forEach(option -> options.add(PlatformAttributeOptionResponse.builder()
                    .id(text(option, "id", "value", "en_name"))
                    .name(text(option, "en_name", "name", "label", "value"))
                    .build()));
        }
        boolean saleProperty = attribute.path("is_sale_prop").asInt(0) == 1;
        return PlatformAttributeResponse.builder()
                .id(text(attribute, "name", "attribute_id"))
                .name(text(attribute, "name", "attribute_id"))
                .label(text(attribute, "label", "name"))
                .inputType(text(attribute, "input_type"))
                .required(attribute.path("is_mandatory").asInt(0) == 1)
                .saleProperty(saleProperty)
                .options(options)
                .build();
    }

    private void collectCategories(JsonNode node, String parentId, List<PlatformCategoryNodeResponse> target) {
        if (node == null || node.isMissingNode() || node.isNull()) return;
        if (node.isArray()) {
            node.forEach(child -> collectCategories(child, parentId, target));
            return;
        }
        String id = text(node, "category_id", "id");
        if (id == null) return;
        target.add(PlatformCategoryNodeResponse.builder()
                .id(id)
                .parentId(parentId)
                .name(text(node, "name", "label"))
                .leaf(node.path("leaf").asBoolean(false))
                .available(true)
                .build());
        collectCategories(node.path("children"), id, target);
    }

    private String accessToken(UUID channelId) {
        return credential(channelId).getAccessToken();
    }

    private Long tokenExpiresAt(UUID channelId) {
        OffsetDateTime value = credential(channelId).getTokenExpiresAt();
        return value == null ? null : value.toEpochSecond();
    }

    private ChannelCredential credential(UUID channelId) {
        return credentialRepository.findByChannelIdAndConnectionState(channelId, "CONNECTED")
                .filter(value -> value.getAccessToken() != null && !value.getAccessToken().isBlank())
                .orElseThrow(() -> new IllegalStateException("Lazada channel is not connected"));
    }

    private JsonNode readTree(String response) {
        try {
            return objectMapper.readTree(response);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot parse Lazada lookup response", e);
        }
    }

    private <T> T cached(Cache cache, Object key, Callable<T> loader) {
        try {
            return cache.get(key, loader);
        } catch (Cache.ValueRetrievalException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Lazada lookup failed", cause);
        }
    }

    private void ensureSuccess(JsonNode root, String apiPath) {
        if (!"0".equals(root.path("code").asText())) {
            throw new IllegalStateException(apiPath + " failed: " + root.path("message").asText("Unknown error"));
        }
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
