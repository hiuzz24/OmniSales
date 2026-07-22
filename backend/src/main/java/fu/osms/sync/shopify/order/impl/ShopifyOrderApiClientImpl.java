package fu.osms.sync.shopify.order.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.token.service.ChannelTokenService;
import fu.osms.sync.shopify.ShopifyShopDomainNormalizer;
import fu.osms.sync.shopify.order.ShopifyOrderApiClient;
import fu.osms.sync.shopify.order.ShopifyOrderPage;
import fu.osms.sync.webhook.WebhookPayloadUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class ShopifyOrderApiClientImpl implements ShopifyOrderApiClient {
    private static final String API_VERSION = "2026-07";
    private static final Pattern NEXT_LINK = Pattern.compile("<([^>]+)>;\\s*rel=\\\"next\\\"");
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ChannelTokenService tokenService;
    private final ShopifyShopDomainNormalizer domainNormalizer;

    @Override
    public ShopifyOrderPage firstPage(Channel channel, OffsetDateTime from, OffsetDateTime to) {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl(channel))
                .queryParam("status", "any").queryParam("updated_at_min", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(from))
                .queryParam("updated_at_max", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(to)).queryParam("limit", 250)
                .build().encode().toUri();
        return execute(channel, uri);
    }

    @Override
    public ShopifyOrderPage nextPage(Channel channel, String pageInfo) {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl(channel)).queryParam("page_info", pageInfo)
                .queryParam("limit", 250).build().encode().toUri();
        return execute(channel, uri);
    }

    private ShopifyOrderPage execute(Channel channel, URI uri) {
        return tokenService.execute(channel.getId(), token -> {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Shopify-Access-Token", token.accessToken());
            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            try {
                Map<String, Object> body = objectMapper.readValue(response.getBody(), new TypeReference<>() {});
                return new ShopifyOrderPage(maps(body.get("orders")), nextPageInfo(response.getHeaders().getFirst(HttpHeaders.LINK)));
            } catch (Exception e) {
                throw new IllegalStateException("Shopify orders response is invalid", e);
            }
        });
    }

    private String baseUrl(Channel channel) {
        String raw = channel.getMetadata() == null ? null : String.valueOf(channel.getMetadata().get("shopDomain"));
        return "https://" + domainNormalizer.canonicalDomain(raw) + "/admin/api/" + API_VERSION + "/orders.json";
    }

    private String nextPageInfo(String link) {
        if (link == null) return null;
        Matcher matcher = NEXT_LINK.matcher(link);
        if (!matcher.find()) return null;
        String query = URI.create(matcher.group(1)).getRawQuery();
        if (query == null) return null;
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            if ("page_info".equals(parts[0]) && parts.length == 2) return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
        }
        return null;
    }

    private List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(Map.class::isInstance).map(WebhookPayloadUtils::copyMap).toList();
    }
}
