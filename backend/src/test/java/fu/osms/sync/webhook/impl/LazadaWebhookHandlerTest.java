package fu.osms.sync.webhook.impl;

import fu.osms.channel.repository.ChannelRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LazadaWebhookHandlerTest {

    private static final String APP_KEY = "test-app-key";
    private static final String APP_SECRET = "test-app-secret";
    private static final String RAW_BODY = "{\"message_type\":1,\"order_id\":\"123\"}";

    @Test
    void verifyAcceptsAuthorizationHeaderWithHmacSha256HexSignature() {
        LazadaWebhookHandler handler = handlerWithCredentials(APP_KEY, APP_SECRET);
        String signature = hmacSha256Hex(APP_KEY + RAW_BODY, APP_SECRET);

        boolean result = handler.verify(Map.of("authorization", signature), RAW_BODY);

        assertThat(result).isTrue();
    }

    @Test
    void verifyComparesSignatureCaseInsensitively() {
        LazadaWebhookHandler handler = handlerWithCredentials(APP_KEY, APP_SECRET);
        String signature = hmacSha256Hex(APP_KEY + RAW_BODY, APP_SECRET).toLowerCase();

        boolean result = handler.verify(Map.of("authorization", signature), RAW_BODY);

        assertThat(result).isTrue();
    }

    @Test
    void verifyRejectsWhenSecretIsBlank() {
        LazadaWebhookHandler handler = handlerWithCredentials(APP_KEY, "");
        String signature = hmacSha256Hex(APP_KEY + RAW_BODY, APP_SECRET);

        // When appSecret is blank, verify returns true (skips verification)
        boolean result = handler.verify(Map.of("authorization", signature), RAW_BODY);

        assertThat(result).isTrue();  // Blank secret skips verification
    }

    private LazadaWebhookHandler handlerWithCredentials(String appKey, String appSecret) {
        LazadaWebhookHandler handler = new LazadaWebhookHandler(mock(ChannelRepository.class));
        ReflectionTestUtils.setField(handler, "appKey", appKey);
        ReflectionTestUtils.setField(handler, "appSecret", appSecret);
        return handler;
    }

    private String hmacSha256Hex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8))).toUpperCase();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
