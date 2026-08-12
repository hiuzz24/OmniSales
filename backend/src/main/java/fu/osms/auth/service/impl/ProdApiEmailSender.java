package fu.osms.auth.service.impl;

import fu.osms.auth.service.EmailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;

@Service
@org.springframework.context.annotation.Profile("render")
public class ProdApiEmailSender implements EmailSender {

    @Value("${email.api.key:}")
    private String apiKey;

    @Value("${email.api.url:https://api.sendgrid.com/v3/mail/send}")
    private String apiUrl;

    @Value("${email.api.from:noreply@yourdomain.com}")
    private String fromEmail;

    @Value("${email.api.from-name:OmniSales}")
    private String fromName;

    private final RestClient restClient = RestClient.create();

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProdApiEmailSender.class);

    @Override
    public void send(String to, String subject, String htmlContent) {
        send(to, subject, htmlContent, true);
    }

    @Override
    public void send(String to, String subject, String content, boolean isHtml) {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("SENDGRID_API_KEY is not set! Please configure your API key.");
            throw new IllegalStateException("SENDGRID_API_KEY environment variable is not configured");
        }

        String contentType = isHtml ? "text/html" : "text/plain";

        try {
            // Build payload using Map + ObjectMapper to avoid string-formatting bugs
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("personalizations", List.of(
                Map.of("to", List.of(Map.of("email", to)))
            ));
            payload.put("from", Map.of("email", fromEmail, "name", fromName));
            payload.put("subject", subject);
            payload.put("content", List.of(
                Map.of("type", contentType, "value", content)
            ));

            String body = new ObjectMapper().writeValueAsString(payload);
            log.info("SendGrid request body: {}", body);

            restClient.post()
                .uri(apiUrl)
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();

            log.info("Email sent successfully to {}", to);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("SendGrid rejected the request. Status: {}, Body: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        } catch (Exception e) {
            log.error("Failed to build/send SendGrid request: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "\"\"";
        return "\"" + s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
            .replace("\t", "\\t") + "\"";
    }
}
