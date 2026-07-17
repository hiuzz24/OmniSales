package fu.osms.sync.tiktok.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Map;

public final class TikTokSignatureUtil {

    private TikTokSignatureUtil() {
    }

    public static String sign(String path,
                              Map<String, ?> queryParameters,
                              String body,
                              String appSecret) {
        StringBuilder base = new StringBuilder(path);
        queryParameters.entrySet().stream()
                .filter(entry -> !"sign".equals(entry.getKey()) && !"access_token".equals(entry.getKey()))
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .forEach(entry -> base.append(entry.getKey()).append(entry.getValue()));
        if (body != null && !body.isBlank()) {
            base.append(body);
        }

        String message = appSecret + base + appSecret;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot sign TikTok Shop API request", e);
        }
    }
}
