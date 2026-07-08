package fu.osms.sync.lazada.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Map;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LazadaSignatureUtil {

    public static String generateSignature(String apiPath, Map<String, String> params, String appSecret) {
        TreeMap<String, String> sortedParams = new TreeMap<>(params);

        StringBuilder query = new StringBuilder(apiPath);
        for (Map.Entry<String, String> entry : sortedParams.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key != null && value != null) {
                query.append(key).append(value);
            }
        }
        log.info("[Laz sign base] {}", query);

        return signWithHmacSha256(query.toString(), appSecret);
    }

    private static String signWithHmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes("UTF-8"), "HmacSHA256");
            mac.init(secretKey);
            byte[] bytes = mac.doFinal(data.getBytes("UTF-8"));

            StringBuilder hash = new StringBuilder();
            for (byte b : bytes) {
                String hex = Integer.toHexString(0xFF & b);
                if (hex.length() == 1) {
                    hash.append('0');
                }
                hash.append(hex);
            }
            return hash.toString().toUpperCase();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate Lazada signature", e);
        }
    }
}
