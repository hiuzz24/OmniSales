package fu.osms.auth.service;

public interface EmailService {
    public void sendForgetPasswordEmail(String toEmail, String token);

    public void sentResetPasswordEmail(String toEmail, String token, String newPassword);

    void sendNotificationEmail(String toEmail, String subject, String body);

    void sendInviteEmail(String toEmail, String token );
}
