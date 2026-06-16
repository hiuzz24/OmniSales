package fu.osms.auth.service;

public interface EmailService {
    public void sendResetPasswordEmail(String toEmail, String token);
}
