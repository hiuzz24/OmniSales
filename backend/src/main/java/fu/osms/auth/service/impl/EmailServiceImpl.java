package fu.osms.auth.service.impl;

import fu.osms.auth.service.EmailService;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl implements EmailService {

    @Autowired
    private JavaMailSender mailSender;

    public EmailServiceImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    @Async
    public void sendForgetPasswordEmail(String toEmail, String token) {
        try {
            String resetLink = "http://localhost:5174/change-password?token=" + token;

            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject("Yêu cầu đặt lại mật khẩu");
            message.setText("Chào bạn,\n\nVui lòng click vào đường dẫn sau để đặt lại mật khẩu của bạn. "
                    + "Liên kết này có hiệu lực trong 15 phút:\n" + resetLink);

            mailSender.send(message);
            
            // Log success (optional)
            System.out.println("Email sent successfully to: " + toEmail);
        } catch (Exception e) {
            // Log error but don't throw exception to avoid breaking async flow
            System.err.println("Failed to send email to " + toEmail + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void sentResetPasswordEmail(String toEmail, String fullName, String newPassword) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");

            helper.setTo(toEmail);
            helper.setSubject("[OSMS] Tài khoản của bạn đã được đặt lại mật khẩu");


            String htmlContent = String.format(
                    "<h3>Xin chào %s,</h3>" +
                            "<p>Quản trị viên hệ thống đã đặt lại mật khẩu cho tài khoản của bạn.</p>" +
                            "<p>Dưới đây là thông tin đăng nhập tạm thời:</p>" +
                            "<ul>" +
                            "  <li><strong>Email:</strong> %s</li>" +
                            "  <li><strong>Mật khẩu tạm thời:</strong> <span style='color: #d9534f; font-family: monospace; font-size: 16px;'>%s</span></li>" +
                            "</ul>" +
                            "<p style='color: #f0ad4e;'><strong>Lưu ý bảo mật:</strong> Mật khẩu này chỉ có giá trị cho lần đăng nhập đầu tiên. Bạn bắt buộc phải thay đổi mật khẩu ngay sau khi đăng nhập thành công vào hệ thống.</p>" +
                            "<p>Trân trọng,<br/>Đội ngũ OSMS.</p>",
                    fullName, toEmail, newPassword
            );

            helper.setText(htmlContent, true);

            mailSender.send(mimeMessage);
            System.out.println("Reset password email sent successfully to: " + toEmail);
        } catch (Exception e) {
            System.err.println("Failed to send reset password email to " + toEmail + ": " + e.getMessage());
            throw new RuntimeException("Email sending failed", e);
        }
    }

    @Override
    @Async
    public void sendNotificationEmail(String toEmail, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Failed to send notification email to " + toEmail + ": " + e.getMessage());
        }
    }
}
