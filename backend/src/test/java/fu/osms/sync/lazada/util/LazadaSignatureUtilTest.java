package fu.osms.sync.lazada.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class LazadaSignatureUtilTest {

    private static final String SECRET = "test-app-secret";

    private static String hmacSha256Hex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8))).toUpperCase();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("generateSignature — deterministic HMAC-SHA256 of sorted apiPath+key+value concat")
    void generateSignature_deterministicVector() {
        Map<String, String> params = new TreeMap<>();
        params.put("app_key", "test-app-key");
        params.put("code", "auth-code");
        params.put("timestamp", "1700000000000");

        String expected = hmacSha256Hex("/auth/token/createapp_keytest-app-keycodeauth-codetimestamp1700000000000", SECRET);

        String sig = LazadaSignatureUtil.generateSignature("/auth/token/create", params, SECRET);

        assertThat(sig).isEqualTo(expected);
        assertThat(sig).isEqualTo(sig.toUpperCase());
    }

    @Test
    @DisplayName("generateSignature — parameter order is sorted, not insertion order")
    void generateSignature_paramOrderSorted() {
        Map<String, String> params = new java.util.LinkedHashMap<>();
        params.put("z_param", "z");
        params.put("a_param", "a");
        params.put("m_param", "m");

        String sig = LazadaSignatureUtil.generateSignature("/path", params, SECRET);

        // Recreate by sorting manually
        TreeMap<String, String> sorted = new TreeMap<>(params);
        StringBuilder concat = new StringBuilder("/path");
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            concat.append(e.getKey()).append(e.getValue());
        }
        String expected = hmacSha256Hex(concat.toString(), SECRET);

        assertThat(sig).isEqualTo(expected);
    }

    @Test
    @DisplayName("generateSignature — null VALUES are skipped (null keys are NPE — by design)")
    void generateSignature_skipsNullValues() {
        Map<String, String> params = new java.util.HashMap<>();
        params.put("app_key", "test-app-key");
        params.put("code", null);
        params.put("good", "value");

        String sig = LazadaSignatureUtil.generateSignature("/x", params, SECRET);

        TreeMap<String, String> sorted = new TreeMap<>();
        sorted.put("app_key", "test-app-key");
        sorted.put("good", "value");
        StringBuilder concat = new StringBuilder("/x");
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            concat.append(e.getKey()).append(e.getValue());
        }
        String expected = hmacSha256Hex(concat.toString(), SECRET);

        assertThat(sig).isEqualTo(expected);
    }

    @Test
    @DisplayName("generateSignature — different apiPath produces different signature")
    void generateSignature_apiPathChangesOutput() {
        Map<String, String> params = Map.of("foo", "bar");
        String s1 = LazadaSignatureUtil.generateSignature("/path1", params, SECRET);
        String s2 = LazadaSignatureUtil.generateSignature("/path2", params, SECRET);
        assertThat(s1).isNotEqualTo(s2);
    }

    @Test
    @DisplayName("generateSignature — empty params only includes apiPath")
    void generateSignature_emptyParams() {
        String sig = LazadaSignatureUtil.generateSignature("/empty", new java.util.HashMap<>(), SECRET);
        String expected = hmacSha256Hex("/empty", SECRET);
        assertThat(sig).isEqualTo(expected);
    }
}
