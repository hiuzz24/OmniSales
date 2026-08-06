package fu.osms.auth.service.impl;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailServiceImpl Tests")
class EmailServiceImplTest {

    private final String frontendUrl = "https://app.osms.local";

    private JavaMailSender mailSender;
    private EmailServiceImpl emailService;
    private MimeMessage mimeMessage;

    private EmailServiceImpl buildService() {
        mailSender = mock(JavaMailSender.class);
        // Real MimeMessage so we can read subject/to/content out
        mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        emailService = new EmailServiceImpl(mailSender, frontendUrl);
        return emailService;
    }

    @Test
    @DisplayName("sendForgetPasswordEmail sends an HTML email whose body contains the reset link")
    void sendForgetPasswordEmail_containsResetLink() throws Exception {
        buildService();
        emailService.sendForgetPasswordEmail("user@example.com", "reset-token-XYZ");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage sent = captor.getValue();

        assertThat(sent.getAllRecipients()).hasSize(1);
        // Addresses only available after prepare
        assertThat(sent.getSubject()).contains("Yêu cầu đặt lại mật khẩu");
        // Verify content by storing to a String – MimeMessageHelper writes the body.
        sent.saveChanges();
        Object content = sent.getContent();
        assertThat(content.toString()).contains("reset-token-XYZ");
        assertThat(content.toString()).contains("/change-password?token=reset-token-XYZ");
    }

    @Test
    @DisplayName("sendForgetPasswordEmail swallows mail-sender exceptions silently (logs only)")
    void sendForgetPasswordEmail_swallowsExceptions() {
        buildService();
        doThrow(new MailException("smtp-down") {}).when(mailSender).send(any(MimeMessage.class));

        // Must not throw — implementation deliberately logs and returns.
        assertThatCode(() -> emailService.sendForgetPasswordEmail("user@example.com", "tok"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sentResetPasswordEmail embeds the new password in the rendered body")
    void sentResetPasswordEmail_embedsPassword() throws Exception {
        buildService();
        emailService.sentResetPasswordEmail("user@example.com", "Nguyễn Văn A", "Temp#2026");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage sent = captor.getValue();
        sent.saveChanges();

        assertThat(sent.getSubject()).contains("đã được đặt lại mật khẩu");
        assertThat(sent.getContent().toString()).contains("Temp#2026");
        assertThat(sent.getContent().toString()).contains("Nguyễn Văn A");
    }

    @Test
    @DisplayName("sentResetPasswordEmail rethrows when SMTP fails (used by admin flow that expects error)")
    void sentResetPasswordEmail_propagatesException() {
        buildService();
        doThrow(new MailException("smtp-down") {}).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> emailService.sentResetPasswordEmail("user@example.com", "Name", "Pwd"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("sendNotificationEmail uses the provided subject verbatim and embeds the body")
    void sendNotificationEmail_usesProvidedSubject() throws Exception {
        buildService();
        emailService.sendNotificationEmail("user@example.com", "My custom subject", "Hello\nworld");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage sent = captor.getValue();
        sent.saveChanges();

        assertThat(sent.getSubject()).isEqualTo("My custom subject");
        // newline turned into <br/>
        assertThat(sent.getContent().toString()).contains("Hello<br/>world");
    }

    @Test
    @DisplayName("sendNotificationEmail falls back to a default subject when caller passes null")
    void sendNotificationEmail_nullSubjectFallback() throws Exception {
        buildService();
        emailService.sendNotificationEmail("user@example.com", null, "body");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        captor.getValue().saveChanges();

        assertThat(captor.getValue().getSubject()).contains("Thông báo hệ thống");
    }

    @Test
    @DisplayName("sendInviteEmail embeds the invite link")
    void sendInviteEmail_containsInviteLink() throws Exception {
        buildService();
        emailService.sendInviteEmail("invitee@example.com", "invite-tok-99");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage sent = captor.getValue();
        sent.saveChanges();

        assertThat(sent.getSubject()).contains("Lời mời tham gia hệ thống");
        assertThat(sent.getContent().toString()).contains("invite-tok-99");
        assertThat(sent.getContent().toString()).contains("/inviteUser?token=invite-tok-99");
    }

    @Test
    @DisplayName("sendInviteEmail swallows mail-sender exceptions silently")
    void sendInviteEmail_swallowsExceptions() {
        buildService();
        doThrow(new MailException("smtp-down") {}).when(mailSender).send(any(MimeMessage.class));

        assertThatCode(() -> emailService.sendInviteEmail("user@example.com", "tok"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Constructor accepts a blank frontend URL without throwing")
    void constructor_acceptsBlankUrl() {
        JavaMailSender sender = mock(JavaMailSender.class);
        // No stubbing — verify the constructors don't throw on null/blank URLs.

        EmailServiceImpl withNull = new EmailServiceImpl(sender, null);
        EmailServiceImpl withEmpty = new EmailServiceImpl(sender, "");
        EmailServiceImpl withSpaces = new EmailServiceImpl(sender, "   ");

        assertThat(withNull).isNotNull();
        assertThat(withEmpty).isNotNull();
        assertThat(withSpaces).isNotNull();
    }
}
