package fu.osms.sync.shopify.impl;

import fu.osms.sync.shopify.ShopifyShopDomainNormalizer;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class ShopifyShopDomainNormalizerImpl implements ShopifyShopDomainNormalizer {

    private static final String SHOPIFY_SUFFIX = ".myshopify.com";
    private static final Pattern HANDLE_PATTERN = Pattern.compile("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$");

    @Override
    public String normalizeHandle(String input) {
        if (!StringUtils.hasText(input)) {
            throw new IllegalArgumentException("Shopify shop must not be blank");
        }

        String candidate = input.trim().toLowerCase(Locale.ROOT);
        String host = hasScheme(candidate) ? parseUrlHost(candidate) : parseBareInput(candidate);
        if (host.endsWith(SHOPIFY_SUFFIX)) {
            host = host.substring(0, host.length() - SHOPIFY_SUFFIX.length());
        } else if (host.contains(".")) {
            throw new IllegalArgumentException("Only {shop}.myshopify.com domains are supported");
        }

        if (!HANDLE_PATTERN.matcher(host).matches()) {
            throw new IllegalArgumentException(
                    "Shopify shop name may only contain lowercase letters, numbers, and hyphens");
        }
        return host;
    }

    @Override
    public String canonicalDomain(String input) {
        return normalizeHandle(input) + SHOPIFY_SUFFIX;
    }

    private boolean hasScheme(String value) {
        return value.matches("^[a-z][a-z0-9+.-]*://.*$");
    }

    private String parseUrlHost(String value) {
        try {
            URI uri = new URI(value);
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("Shopify shop URL must use HTTP or HTTPS");
            }
            if (uri.getUserInfo() != null || uri.getPort() != -1 || !StringUtils.hasText(uri.getHost())) {
                throw new IllegalArgumentException("Shopify shop URL is invalid");
            }
            return uri.getHost().toLowerCase(Locale.ROOT);
        } catch (URISyntaxException error) {
            throw new IllegalArgumentException("Shopify shop URL is invalid", error);
        }
    }

    private String parseBareInput(String value) {
        int delimiter = firstDelimiter(value);
        String host = delimiter >= 0 ? value.substring(0, delimiter) : value;
        if (host.contains(":") || host.contains("@")) {
            throw new IllegalArgumentException("Shopify shop URL must not contain a port or user info");
        }
        return host;
    }

    private int firstDelimiter(String value) {
        int result = -1;
        for (char delimiter : new char[]{'/', '?', '#'}) {
            int index = value.indexOf(delimiter);
            if (index >= 0 && (result < 0 || index < result)) {
                result = index;
            }
        }
        return result;
    }
}
