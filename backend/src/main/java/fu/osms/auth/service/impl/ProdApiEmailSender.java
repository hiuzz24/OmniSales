package fu.osms.auth.service.impl;

import fu.osms.auth.service.EmailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@org.springframework.context.annotation.Profile("prod")
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

    @Override
    public void send(String to, String subject, String htmlContent) {
        send(to, subject, htmlContent, true);
    }

    @Override
    public void send(String to, String subject, String content, boolean isHtml) {
        String contentType = isHtml ? "text/html" : "text/plain";

        String body = """
            {
              "personalizations": [{"to": [{"email": "%s"}]}],
              "from": {"email": "%s", "name": "%s"},
              "subject": "%s",
              "content": [{"type": "%s", "value": %s}]
            }
            """.formatted(
                to,
                fromEmail,
                escapeJson(fromName),
                escapeJson(subject),
                contentType,
                escapeJson(content)
        );

        restClient.post()
            .uri(apiUrl)
            .header("Authorization", "Bearer " + apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .toBodilessEntity();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return "\"" + s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
            .replace("\t", "\\t") + "\"";
    }
}
