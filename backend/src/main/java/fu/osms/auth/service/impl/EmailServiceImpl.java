package fu.osms.auth.service.impl;

import fu.osms.auth.service.EmailService;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl implements EmailService {

    @Value("${app.frontend-url:http://localhost:5174}")
    private String frontendUrl = "http://localhost:5174";

    private final JavaMailSender mailSender;

    @Autowired
    public EmailServiceImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public EmailServiceImpl(JavaMailSender mailSender, String frontendUrl) {
        this.mailSender = mailSender;
        if (frontendUrl != null && !frontendUrl.isBlank()) {
            this.frontendUrl = frontendUrl;
        }
    }

    @Override
    @Async
    public void sendForgetPasswordEmail(String toEmail, String token) {
        try {
            String baseUrl = getCleanFrontendUrl();
            String resetLink = baseUrl + "/change-password?token=" + token;

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");

            helper.setTo(toEmail);
            helper.setSubject("[OmniSales] Yêu cầu đặt lại mật khẩu tài khoản");

            String title = "Bảo mật tài khoản của bạn";
            String subtitleHtml = "Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản OmniSales của bạn.<br/>Vui lòng nhấn vào nút bên dưới để tiến hành đặt lại mật khẩu.";

            String contentHtml = """
                <div style="text-align: center; margin: 32px 0;">
                    <a href="%s" target="_blank" style="display: inline-block; background: linear-gradient(135deg, #4f46e5 0%%, #4338ca 100%%); color: #ffffff; font-size: 15px; font-weight: 600; text-decoration: none; padding: 14px 32px; border-radius: 8px; box-shadow: 0 4px 12px rgba(79, 70, 229, 0.3);">
                        Đặt lại mật khẩu
                    </a>
                </div>
                <div style="background-color: #f8fafc; border: 1px dashed #cbd5e1; border-radius: 8px; padding: 14px; margin-top: 24px; text-align: center;">
                    <span style="font-size: 12px; color: #64748b; display: block; margin-bottom: 6px;">Hoặc sao chép và dán liên kết sau vào trình duyệt:</span>
                    <a href="%s" style="font-size: 12px; color: #2563eb; word-break: break-all; font-family: monospace; text-decoration: underline;">%s</a>
                </div>
                """.formatted(resetLink, resetLink, resetLink);

            String securityNoticeHtml = "Liên kết này có hiệu lực trong <strong>15 phút</strong>.<br/>Nếu bạn không gửi yêu cầu đặt lại mật khẩu, bạn có thể an tâm bỏ qua email này.";

            String htmlContent = buildGitLabStyleEmailHtml(title, subtitleHtml, contentHtml, securityNoticeHtml);
            helper.setText(htmlContent, true);

            mailSender.send(mimeMessage);
            System.out.println("Forget password email sent successfully to: " + toEmail);
        } catch (Exception e) {
            System.err.println("Failed to send forget password email to " + toEmail + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void sentResetPasswordEmail(String toEmail, String fullName, String newPassword) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");

            helper.setTo(toEmail);
            helper.setSubject("[OmniSales] Tài khoản của bạn đã được đặt lại mật khẩu");

            String title = "Thông tin mật khẩu mới";
            String subtitleHtml = "Xin chào <strong>" + escapeHtml(fullName) + "</strong>,<br/>Quản trị viên hệ thống đã đặt lại mật khẩu cho tài khoản của bạn. Dưới đây là thông tin đăng nhập tạm thời:";

            String baseUrl = getCleanFrontendUrl();
            String loginLink = baseUrl + "/login";

            String contentHtml = """
                <div style="background-color: #f8fafc; border: 1px solid #e2e8f0; border-radius: 10px; padding: 20px 24px; margin: 24px 0;">
                    <div style="margin-bottom: 12px; font-size: 13px; color: #475569;">
                        <strong style="color: #0f172a;">Tài khoản email:</strong> %s
                    </div>
                    <div style="font-size: 13px; color: #475569; margin-bottom: 8px;">
                        <strong style="color: #0f172a;">Mật khẩu tạm thời:</strong>
                    </div>
                    <div style="background-color: #ffffff; border: 1px solid #cbd5e1; border-radius: 8px; padding: 16px; text-align: center; box-shadow: inset 0 1px 2px rgba(0,0,0,0.03);">
                        <span style="font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace; font-size: 24px; font-weight: 700; color: #d97706; letter-spacing: 2px;">%s</span>
                    </div>
                </div>
                <div style="text-align: center; margin: 32px 0;">
                    <a href="%s" target="_blank" style="display: inline-block; background: linear-gradient(135deg, #4f46e5 0%%, #4338ca 100%%); color: #ffffff; font-size: 15px; font-weight: 600; text-decoration: none; padding: 14px 32px; border-radius: 8px; box-shadow: 0 4px 12px rgba(79, 70, 229, 0.3);">
                        Đăng nhập ngay
                    </a>
                </div>
                """.formatted(escapeHtml(toEmail), escapeHtml(newPassword), loginLink);

            String securityNoticeHtml = "<strong style=\"color: #b45309;\">Lưu ý bảo mật:</strong> Mật khẩu tạm thời này chỉ có giá trị cho lần đăng nhập đầu tiên. Bạn <strong>bắt buộc phải thay đổi mật khẩu</strong> ngay sau khi đăng nhập thành công.";

            String htmlContent = buildGitLabStyleEmailHtml(title, subtitleHtml, contentHtml, securityNoticeHtml);
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
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");

            helper.setTo(toEmail);
            String safeSubject = subject != null ? subject : "[OmniSales] Thông báo hệ thống";
            helper.setSubject(safeSubject);

            String title = "Thông báo hệ thống";
            String subtitleHtml = escapeHtml(safeSubject);

            String contentHtml = """
                <div style="background-color: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 20px; margin: 20px 0; font-size: 14px; line-height: 1.6; color: #334155;">
                    %s
                </div>
                """.formatted(escapeHtml(body != null ? body : "").replace("\n", "<br/>"));

            String securityNoticeHtml = "Đây là thông báo tự động từ hệ thống quản lý OmniSales.";

            String htmlContent = buildGitLabStyleEmailHtml(title, subtitleHtml, contentHtml, securityNoticeHtml);
            helper.setText(htmlContent, true);

            mailSender.send(mimeMessage);
        } catch (Exception e) {
            System.err.println("Failed to send notification email to " + toEmail + ": " + e.getMessage());
        }
    }

    @Override
    @Async
    public void sendInviteEmail(String toEmail, String token) {
        try {
            String baseUrl = getCleanFrontendUrl();
            String inviteLink = baseUrl + "/inviteUser?token=" + token;

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");

            helper.setTo(toEmail);
            helper.setSubject("[OmniSales] Lời mời tham gia hệ thống OmniSales");

            String title = "Lời mời tham gia hệ thống";
            String subtitleHtml = "Chào bạn,<br/>Bạn đã được mời trở thành thành viên trên hệ thống quản lý bán hàng <strong>OmniSales</strong>.<br/>Vui lòng nhấn vào nút bên dưới để tạo tài khoản của bạn.";

            String contentHtml = """
                <div style="text-align: center; margin: 32px 0;">
                    <a href="%s" target="_blank" style="display: inline-block; background: linear-gradient(135deg, #4f46e5 0%%, #4338ca 100%%); color: #ffffff; font-size: 15px; font-weight: 600; text-decoration: none; padding: 14px 32px; border-radius: 8px; box-shadow: 0 4px 12px rgba(79, 70, 229, 0.3);">
                        Chấp nhận lời mời & Tạo tài khoản
                    </a>
                </div>
                <div style="background-color: #f8fafc; border: 1px dashed #cbd5e1; border-radius: 8px; padding: 14px; margin-top: 24px; text-align: center;">
                    <span style="font-size: 12px; color: #64748b; display: block; margin-bottom: 6px;">Hoặc sao chép và dán liên kết sau vào trình duyệt:</span>
                    <a href="%s" style="font-size: 12px; color: #2563eb; word-break: break-all; font-family: monospace; text-decoration: underline;">%s</a>
                </div>
                """.formatted(inviteLink, inviteLink, inviteLink);

            String securityNoticeHtml = "Liên kết này có hiệu lực trong <strong>15 phút</strong>.<br/>Nếu bạn không mong đợi lời mời này, bạn có thể an tâm bỏ qua email này.";

            String htmlContent = buildGitLabStyleEmailHtml(title, subtitleHtml, contentHtml, securityNoticeHtml);
            helper.setText(htmlContent, true);

            mailSender.send(mimeMessage);

            System.out.println("Invite email sent successfully to: " + toEmail);
        } catch (Exception e) {
            System.err.println("Failed to send invite email to " + toEmail + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String buildGitLabStyleEmailHtml(String title, String subtitleHtml, String contentHtml, String securityNoticeHtml) {
        return """
            <!DOCTYPE html>
            <html lang="vi">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>OmniSales Email</title>
            </head>
            <body style="margin: 0; padding: 0; background-color: #f4f5f7; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; -webkit-font-smoothing: antialiased; color: #1e293b;">
                <table width="100%%" cellpadding="0" cellspacing="0" border="0" style="background-color: #f4f5f7; table-layout: fixed;">
                    <tr>
                        <td align="center" style="padding: 0;">
                            <div style="height: 4px; background: linear-gradient(90deg, #4f46e5 0%%, #6366f1 50%%, #3b82f6 100%%); width: 100%%;"></div>
                        </td>
                    </tr>
                    <tr>
                        <td align="center" style="padding: 36px 16px 48px 16px;">
                            <table width="100%%" cellpadding="0" cellspacing="0" border="0" style="max-width: 560px; margin: 0 auto;">
                                
                                <!-- Header Brand Logo -->
                                <tr>
                                    <td align="center" style="padding-bottom: 24px;">
                                        <table cellpadding="0" cellspacing="0" border="0">
                                            <tr>
                                                <td align="center" style="background: linear-gradient(135deg, #4f46e5 0%%, #3b82f6 100%%); padding: 12px; border-radius: 12px; box-shadow: 0 4px 12px rgba(79, 70, 229, 0.25);">
                                                    <svg width="32" height="32" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                                                        <path d="M12 2L2 7L12 12L22 7L12 2Z" stroke="#ffffff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                                                        <path d="M2 17L12 22L22 17" stroke="#ffffff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                                                        <path d="M2 12L12 17L22 12" stroke="#ffffff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                                                    </svg>
                                                </td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>

                                <!-- Main Card Box -->
                                <tr>
                                    <td>
                                        <table width="100%%" cellpadding="0" cellspacing="0" border="0" style="background-color: #ffffff; border-radius: 12px; border: 1px solid #e2e8f0; box-shadow: 0 4px 16px rgba(0, 0, 0, 0.04); overflow: hidden;">
                                            <tr>
                                                <td style="padding: 40px 36px 36px 36px;">
                                                    
                                                    <h1 style="margin: 0 0 16px 0; font-size: 22px; font-weight: 700; color: #0f172a; text-align: center; line-height: 1.3;">
                                                        %s
                                                    </h1>
                                                    
                                                    <div style="margin: 0 0 28px 0; font-size: 14px; line-height: 1.6; color: #475569; text-align: center;">
                                                        %s
                                                    </div>

                                                    %s

                                                    <div style="margin-top: 28px; font-size: 12px; line-height: 1.5; color: #64748b; text-align: center; border-top: 1px solid #f1f5f9; padding-top: 20px;">
                                                        %s
                                                    </div>

                                                </td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>

                                <!-- Footer -->
                                <tr>
                                    <td align="center" style="padding-top: 24px; text-align: center;">
                                        <div style="font-size: 14px; font-weight: 700; color: #475569; letter-spacing: 0.5px; margin-bottom: 6px;">
                                            OmniSales
                                        </div>
                                        <p style="margin: 0; font-size: 12px; color: #94a3b8; line-height: 1.5;">
                                            Bạn nhận được email này từ hệ thống quản lý bán hàng <strong style="color: #64748b;">OmniSales</strong>.<br/>
                                            Đây là email tự động, vui lòng không trả lời trực tiếp email này.
                                        </p>
                                    </td>
                                </tr>

                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
            """.formatted(title, subtitleHtml, contentHtml, securityNoticeHtml);
    }

    private String getCleanFrontendUrl() {
        if (frontendUrl == null || frontendUrl.isBlank()) {
            return "http://localhost:5174";
        }
        return frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }
}

