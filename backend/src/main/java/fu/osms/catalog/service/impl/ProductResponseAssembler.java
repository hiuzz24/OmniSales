package fu.osms.catalog.service.impl;

import fu.osms.catalog.dto.response.ProductImageResponse;
import fu.osms.catalog.dto.response.ProductResponse;
import fu.osms.catalog.dto.response.ProductVariantResponse;
import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductImage;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.mapper.ProductImageMapper;
import fu.osms.catalog.mapper.ProductMapper;
import fu.osms.catalog.mapper.ProductVariantMapper;
import fu.osms.catalog.repository.ProductImageRepository;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.channel.dto.response.ChannelSyncResponse;
import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.channel.service.ChannelService;
import fu.osms.common.dto.PageResponse;
import fu.osms.common.enums.PlatformType;
import fu.osms.inventory.dto.response.StockSummaryDTO;
import fu.osms.inventory.service.InventoryService;
import fu.osms.order.repository.OrderItemRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

final class ProductResponseAssembler {

    private final ProductMapper productMapper;
    private final ProductVariantMapper productVariantMapper;
    private final ProductImageMapper productImageMapper;
    private final ProductVariantRepository productVariantRepository;
    private final ProductImageRepository productImageRepository;
    private final InventoryService inventoryService;
    private final ChannelService channelService;
    private final OrderItemRepository orderItemRepository;
    private final ChannelProductVariantRepository channelProductVariantRepository;

    ProductResponseAssembler(ProductMapper productMapper,
                             ProductVariantMapper productVariantMapper,
                             ProductImageMapper productImageMapper,
                             ProductVariantRepository productVariantRepository,
                             ProductImageRepository productImageRepository,
                             InventoryService inventoryService,
                             ChannelService channelService,
                             OrderItemRepository orderItemRepository,
                             ChannelProductVariantRepository channelProductVariantRepository) {
        this.productMapper = productMapper;
        this.productVariantMapper = productVariantMapper;
        this.productImageMapper = productImageMapper;
        this.productVariantRepository = productVariantRepository;
        this.productImageRepository = productImageRepository;
        this.inventoryService = inventoryService;
        this.channelService = channelService;
        this.orderItemRepository = orderItemRepository;
        this.channelProductVariantRepository = channelProductVariantRepository;
    }

    ProductResponse assemble(Product product) {
        UUID productId = product.getId();
        ProductResponse response = productMapper.toResponse(product);
        response.setHasOrders(orderItemRepository.existsByVariant_Product_Id(productId));
        List<ProductImage> images = productImageRepository
                .findByProductIdOrderByIsPrimaryDescSortOrderAsc(productId);
        response.setImages(images.stream()
                .filter(image -> image.getVariant() == null)
                .map(productImageMapper::toResponse)
                .toList());

        List<ProductVariant> variants = productVariantRepository.findByProductIdAndDeletedAtIsNull(productId);
        List<UUID> variantIds = variants.stream().map(ProductVariant::getId).toList();
        Map<UUID, StockSummaryDTO> stockByVariant = inventoryService.getStockSummary(variantIds);
        Map<UUID, String> marketplaceSkuByVariant = loadMarketplaceSkuByVariantId(variantIds);
        response.setVariants(variants.stream()
                .map(variant -> variantResponse(variant, images, stockByVariant, marketplaceSkuByVariant))
                .toList());
        applyProductDisplaySku(response);
        response.setChannels(channelService.getProductChannels(List.of(productId))
                .getOrDefault(productId, Collections.emptyList()));
        response.setChannelIds(channelService.getProductChannelIds(List.of(productId))
                .getOrDefault(productId, Collections.emptyList()));
        response.setChannelSyncs(channelService.getProductChannelSyncs(List.of(productId))
                .getOrDefault(productId, Collections.emptyList()));
        return response;
    }

    PageResponse<ProductResponse> search(List<Product> source,
                                         String keyword,
                                         Collection<PlatformType> platforms,
                                         int page,
                                         int size) {
        List<ProductResponse> products = buildProductResponses(source);
        products = aggregateProductResponsesBySku(products);
        products = filterByPlatforms(products, platforms);
        products = filterByKeyword(products, keyword);
        int totalPages = totalPages(products.size(), size);
        return PageResponse.<ProductResponse>builder()
                .content(paginate(products, page, size))
                .page(page)
                .size(size)
                .totalElements(products.size())
                .totalProducts((long) products.size())
                .totalPages(totalPages)
                .first(page <= 0)
                .last(page >= totalPages - 1)
                .build();
    }

    private List<ProductResponse> buildProductResponses(List<Product> products) {
        List<ProductResponse> responses = products.stream().map(productMapper::toResponse).toList();
        List<UUID> productIds = responses.stream().map(ProductResponse::getId).toList();
        if (productIds.isEmpty()) {
            return responses;
        }

        List<ProductVariant> variants = productVariantRepository.findByProductIdInAndDeletedAtIsNull(productIds);
        List<ProductImage> images = productImageRepository
                .findByProductIdInOrderByIsPrimaryDescSortOrderAsc(productIds);
        List<UUID> variantIds = variants.stream().map(ProductVariant::getId).toList();
        Map<UUID, StockSummaryDTO> stockByVariant = inventoryService.getStockSummary(variantIds);
        Map<UUID, String> marketplaceSkuByVariant = loadMarketplaceSkuByVariantId(variantIds);
        Map<UUID, List<String>> channelsByProduct = channelService.getProductChannels(productIds);
        Map<UUID, List<UUID>> channelIdsByProduct = channelService.getProductChannelIds(productIds);
        Map<UUID, List<ChannelSyncResponse>> syncsByProduct = channelService.getProductChannelSyncs(productIds);
        Map<UUID, List<ProductVariant>> variantsByProduct = variants.stream()
                .collect(Collectors.groupingBy(variant -> variant.getProduct().getId()));
        Map<UUID, List<ProductImage>> imagesByProduct = images.stream()
                .collect(Collectors.groupingBy(image -> image.getProduct().getId()));

        responses.forEach(response -> {
            UUID productId = response.getId();
            List<ProductImage> productImages = imagesByProduct.getOrDefault(productId, List.of());
            response.setImages(productImages.stream()
                    .filter(image -> image.getVariant() == null)
                    .map(productImageMapper::toResponse)
                    .toList());
            response.setVariants(variantsByProduct.getOrDefault(productId, List.of()).stream()
                    .map(variant -> variantResponse(
                            variant, productImages, stockByVariant, marketplaceSkuByVariant))
                    .toList());
            applyProductDisplaySku(response);
            response.setChannels(channelsByProduct.getOrDefault(productId, List.of()));
            response.setChannelIds(channelIdsByProduct.getOrDefault(productId, List.of()));
            response.setChannelSyncs(syncsByProduct.getOrDefault(productId, List.of()));
        });
        return responses;
    }

    private ProductVariantResponse variantResponse(ProductVariant variant,
                                                   List<ProductImage> productImages,
                                                   Map<UUID, StockSummaryDTO> stockByVariant,
                                                   Map<UUID, String> marketplaceSkuByVariant) {
        ProductVariantResponse response = productVariantMapper.toResponse(variant);
        String marketplaceSku = marketplaceSkuByVariant.get(variant.getId());
        if (marketplaceSku != null) {
            response.setMarketplaceSku(marketplaceSku);
        }
        StockSummaryDTO stock = stockByVariant.get(variant.getId());
        if (stock != null) {
            response.setAvailableQuantity(stock.getAvailableQuantity());
            response.setQuantityOnHand(stock.getQuantityOnHand());
        }
        response.setImages(productImages.stream()
                .filter(image -> image.getVariant() != null
                        && image.getVariant().getId().equals(variant.getId()))
                .map(productImageMapper::toResponse)
                .toList());
        return response;
    }

    private Map<UUID, String> loadMarketplaceSkuByVariantId(Collection<UUID> variantIds) {
        if (variantIds == null || variantIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> result = new HashMap<>();
        List<ChannelProductVariant> mappings = channelProductVariantRepository
                .findActiveByVariantIdInWithChannel(new ArrayList<>(variantIds));
        for (ChannelProductVariant mapping : mappings) {
            if (mapping.getVariant() == null || mapping.getVariant().getId() == null) {
                continue;
            }
            String externalSku = normalizeSkuValue(mapping.getExternalSku());
            if (externalSku != null) {
                result.putIfAbsent(mapping.getVariant().getId(), externalSku);
            }
        }
        return result;
    }

    private void applyProductDisplaySku(ProductResponse product) {
        String marketplaceSku = product.getVariants() == null ? null : product.getVariants().stream()
                .map(ProductVariantResponse::getMarketplaceSku)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
        if (marketplaceSku != null) {
            product.setMarketplaceSku(marketplaceSku);
        }
        if (product.getProductIds() == null || product.getProductIds().isEmpty()) {
            product.setProductIds(product.getId() == null ? List.of() : List.of(product.getId()));
        }
    }

    private List<ProductResponse> aggregateProductResponsesBySku(List<ProductResponse> products) {
        if (products == null || products.isEmpty()) {
            return List.of();
        }
        int[] parents = new int[products.size()];
        for (int index = 0; index < parents.length; index++) {
            parents[index] = index;
        }
        Map<String, Integer> ownerBySku = new LinkedHashMap<>();
        for (int index = 0; index < products.size(); index++) {
            for (String key : productAggregateKeys(products.get(index))) {
                Integer owner = ownerBySku.putIfAbsent(key, index);
                if (owner != null) {
                    union(parents, owner, index);
                }
            }
        }
        Map<Integer, List<ProductResponse>> groups = new LinkedHashMap<>();
        for (int index = 0; index < products.size(); index++) {
            groups.computeIfAbsent(find(parents, index), ignored -> new ArrayList<>()).add(products.get(index));
        }
        List<ProductResponse> result = new ArrayList<>();
        groups.values().forEach(group -> result.add(aggregateProductGroup(group)));
        return result;
    }

    private ProductResponse aggregateProductGroup(List<ProductResponse> group) {
        ProductResponse first = group.get(0);
        if (group.size() == 1) {
            return first;
        }
        LinkedHashSet<UUID> productIds = new LinkedHashSet<>();
        LinkedHashSet<UUID> channelIds = new LinkedHashSet<>();
        LinkedHashSet<String> channels = new LinkedHashSet<>();
        List<ChannelSyncResponse> syncs = new ArrayList<>();
        List<ProductVariantResponse> variants = new ArrayList<>();
        List<ProductImageResponse> images = new ArrayList<>();
        for (ProductResponse product : group) {
            if (product.getId() != null) productIds.add(product.getId());
            if (product.getProductIds() != null) productIds.addAll(product.getProductIds());
            if (product.getChannelIds() != null) channelIds.addAll(product.getChannelIds());
            if (product.getChannels() != null) channels.addAll(product.getChannels());
            if (product.getChannelSyncs() != null) syncs.addAll(product.getChannelSyncs());
            if (product.getVariants() != null) variants.addAll(product.getVariants());
            if (product.getImages() != null) images.addAll(product.getImages());
        }
        first.setProductIds(new ArrayList<>(productIds));
        first.setChannels(new ArrayList<>(channels));
        first.setChannelIds(new ArrayList<>(channelIds));
        first.setChannelSyncs(distinctChannelSyncs(syncs));
        first.setVariants(aggregateVariantsBySku(variants));
        if ((first.getImages() == null || first.getImages().isEmpty()) && !images.isEmpty()) {
            first.setImages(images);
        }
        applyProductDisplaySku(first);
        return first;
    }

    private int find(int[] parents, int index) {
        if (parents[index] != index) parents[index] = find(parents, parents[index]);
        return parents[index];
    }

    private void union(int[] parents, int left, int right) {
        int leftRoot = find(parents, left);
        int rightRoot = find(parents, right);
        if (leftRoot != rightRoot) parents[rightRoot] = leftRoot;
    }

    private List<ProductVariantResponse> aggregateVariantsBySku(List<ProductVariantResponse> variants) {
        if (variants == null || variants.isEmpty()) return List.of();
        Map<String, List<ProductVariantResponse>> groups = variants.stream().collect(Collectors.groupingBy(
                this::variantAggregateKey, LinkedHashMap::new, Collectors.toList()));
        List<ProductVariantResponse> result = new ArrayList<>();
        for (List<ProductVariantResponse> group : groups.values()) {
            ProductVariantResponse first = group.get(0);
            ProductVariantResponse stockSource = group.stream().max(Comparator
                    .comparingInt((ProductVariantResponse item) -> safeInt(item.getQuantityOnHand()))
                    .thenComparingInt(item -> safeInt(item.getAvailableQuantity()))).orElse(first);
            BigDecimal price = group.stream().map(ProductVariantResponse::getPrice)
                    .filter(value -> value != null && value.compareTo(BigDecimal.ZERO) > 0)
                    .findFirst().orElse(first.getPrice());
            String sku = firstNonBlank(first.getMarketplaceSku(), first.getSku());
            first.setSku(sku);
            first.setMarketplaceSku(sku);
            first.setPrice(price);
            first.setAvailableQuantity(safeInt(stockSource.getAvailableQuantity()));
            first.setQuantityOnHand(safeInt(stockSource.getQuantityOnHand()));
            result.add(first);
        }
        return result;
    }

    private List<ChannelSyncResponse> distinctChannelSyncs(List<ChannelSyncResponse> syncs) {
        Map<UUID, ChannelSyncResponse> byChannel = new LinkedHashMap<>();
        syncs.stream().filter(Objects::nonNull).filter(sync -> sync.getChannelId() != null)
                .forEach(sync -> byChannel.putIfAbsent(sync.getChannelId(), sync));
        return new ArrayList<>(byChannel.values());
    }

    private List<ProductResponse> filterByKeyword(List<ProductResponse> products, String keyword) {
        if (keyword == null || keyword.isBlank()) return products;
        String needle = keyword.trim().toLowerCase(Locale.ROOT);
        return products.stream().filter(product -> contains(product.getName(), needle)
                || contains(product.getSku(), needle)
                || contains(product.getMarketplaceSku(), needle)
                || product.getVariants() != null && product.getVariants().stream().anyMatch(variant ->
                contains(variant.getSku(), needle) || contains(variant.getMarketplaceSku(), needle)
                        || contains(variant.getName(), needle))).toList();
    }

    private List<ProductResponse> filterByPlatforms(List<ProductResponse> products,
                                                    Collection<PlatformType> platforms) {
        if (platforms == null || platforms.isEmpty() || products == null || products.isEmpty()) return products;
        Set<String> required = platforms.stream().filter(Objects::nonNull)
                .map(platform -> platform.name().toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return required.isEmpty() ? products : products.stream()
                .filter(product -> productPlatforms(product).containsAll(required)).toList();
    }

    private Set<String> productPlatforms(ProductResponse product) {
        LinkedHashSet<String> platforms = new LinkedHashSet<>();
        if (product.getChannels() != null) product.getChannels().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(Locale.ROOT)).forEach(platforms::add);
        if (product.getChannelSyncs() != null) product.getChannelSyncs().stream()
                .map(ChannelSyncResponse::getPlatform).filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(Locale.ROOT)).forEach(platforms::add);
        return platforms;
    }

    private Set<String> productAggregateKeys(ProductResponse product) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        addSkuKey(keys, product.getMarketplaceSku());
        addSkuKey(keys, product.getSku());
        if (product.getVariants() != null) product.getVariants().forEach(variant -> {
            addSkuKey(keys, variant.getMarketplaceSku());
            addSkuKey(keys, variant.getSku());
        });
        if (keys.isEmpty()) keys.add(product.getId() == null ? UUID.randomUUID().toString() : "product:" + product.getId());
        return keys;
    }

    private void addSkuKey(Set<String> keys, String sku) {
        String normalized = normalizeSkuKey(sku);
        if (!normalized.isBlank()) keys.add("sku:" + normalized);
    }

    private String variantAggregateKey(ProductVariantResponse variant) {
        String sku = normalizeSkuKey(firstNonBlank(variant.getMarketplaceSku(), variant.getSku()));
        return !sku.isBlank() ? "sku:" + sku
                : variant.getId() == null ? UUID.randomUUID().toString() : "variant:" + variant.getId();
    }

    private boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private String normalizeSkuValue(String sku) {
        return sku == null || sku.isBlank() ? null : sku.trim();
    }

    private String normalizeSkuKey(String sku) {
        return sku == null ? "" : sku.trim().toLowerCase(Locale.ROOT);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private List<ProductResponse> paginate(List<ProductResponse> source, int page, int size) {
        if (source == null || source.isEmpty()) return List.of();
        int safeSize = Math.max(size, 1);
        int from = Math.min(Math.max(page, 0) * safeSize, source.size());
        return source.subList(from, Math.min(from + safeSize, source.size()));
    }

    private int totalPages(int totalElements, int size) {
        return (int) Math.ceil((double) totalElements / Math.max(size, 1));
    }
}
