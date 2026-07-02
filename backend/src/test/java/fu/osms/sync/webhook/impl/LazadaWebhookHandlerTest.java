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

    private static final String SECRET = "test-secret";
    private static final String RAW_BODY = "{\"message_type\":1,\"order_id\":\"123\"}";

    @Test
    void verifyAcceptsAuthorizationHeaderWithHmacSha256HexSignature() {
        LazadaWebhookHandler handler = handlerWithSecret(SECRET);
        String signature = hmacSha256Hex(RAW_BODY, SECRET);

        boolean result = handler.verify(Map.of("Authorization", signature), RAW_BODY);

        assertThat(result).isTrue();
    }

    @Test
    void verifyComparesSignatureCaseInsensitively() {
        LazadaWebhookHandler handler = handlerWithSecret(SECRET);
        String signature = hmacSha256Hex(RAW_BODY, SECRET).toLowerCase();

        boolean result = handler.verify(Map.of("authorization", signature), RAW_BODY);

        assertThat(result).isTrue();
    }

    @Test
    void verifyRejectsWhenSecretIsMissing() {
        LazadaWebhookHandler handler = handlerWithSecret("");
        String signature = hmacSha256Hex(RAW_BODY, SECRET);

        boolean result = handler.verify(Map.of("authorization", signature), RAW_BODY);

        assertThat(result).isFalse();
    }

    private LazadaWebhookHandler handlerWithSecret(String secret) {
        LazadaWebhookHandler handler = new LazadaWebhookHandler(mock(ChannelRepository.class));
        ReflectionTestUtils.setField(handler, "webhookSecret", secret);
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
