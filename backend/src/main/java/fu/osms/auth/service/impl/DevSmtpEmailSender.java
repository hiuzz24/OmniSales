package fu.osms.auth.service.impl;

import fu.osms.auth.service.EmailSender;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@org.springframework.context.annotation.Profile({"dev", "default"})
public class DevSmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;

    public DevSmtpEmailSender(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void send(String to, String subject, String htmlContent) {
        send(to, subject, htmlContent, true);
    }

    @Override
    public void send(String to, String subject, String content, boolean isHtml) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, isHtml);
            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            throw new RuntimeException("SMTP send failed: " + e.getMessage(), e);
        }
    }
}
