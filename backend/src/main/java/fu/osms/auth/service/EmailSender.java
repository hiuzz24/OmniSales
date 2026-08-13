package fu.osms.auth.service;

public interface EmailSender {
    void send(String to, String subject, String htmlContent);
    void send(String to, String subject, String content, boolean isHtml);
}
