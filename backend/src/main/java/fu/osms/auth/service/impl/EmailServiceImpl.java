package fu.osms.auth.service.impl;

import fu.osms.auth.service.EmailService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl implements EmailService {

    @Autowired
    private JavaMailSender mailSender;

    public EmailServiceImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void sendResetPasswordEmail(String toEmail, String token) {
        String resetLink = "http://localhost:5173/change-password?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Yêu cầu đặt lại mật khẩu");
        message.setText("Chào bạn,\n\nVui lòng click vào đường dẫn sau để đặt lại mật khẩu của bạn. "
                + "Liên kết này có hiệu lực trong 15 phút:\n" + resetLink);

        mailSender.send(message);
    }
}
