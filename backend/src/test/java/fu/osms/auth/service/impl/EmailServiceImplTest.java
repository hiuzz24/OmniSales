package fu.osms.auth.service.impl;

import fu.osms.auth.service.EmailSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailServiceImpl Tests")
class EmailServiceImplTest {

    private final String frontendUrl = "https://app.osms.local";

    private EmailSender emailSender;
    private EmailServiceImpl emailService;

    @Captor
    private ArgumentCaptor<String> toCaptor;

    @Captor
    private ArgumentCaptor<String> subjectCaptor;

    @Captor
    private ArgumentCaptor<String> contentCaptor;

    private EmailServiceImpl buildService() {
        emailSender = mock(EmailSender.class);
        emailService = new EmailServiceImpl(emailSender, frontendUrl);
        return emailService;
    }

    @Test
    @DisplayName("sendForgetPasswordEmail sends email with reset link")
    void sendForgetPasswordEmail_containsResetLink() {
        buildService();
        emailService.sendForgetPasswordEmail("user@example.com", "reset-token-XYZ");

        verify(emailSender).send(
                eq("user@example.com"),
                contains("đặt lại mật khẩu"),
                contains("reset-token-XYZ")
        );
    }

    @Test
    @DisplayName("sendForgetPasswordEmail swallows mail-sender exceptions silently (logs only)")
    void sendForgetPasswordEmail_swallowsExceptions() {
        buildService();
        doThrow(new RuntimeException("smtp-down")).when(emailSender).send(any(), any(), any());

        assertThatCode(() -> emailService.sendForgetPasswordEmail("user@example.com", "tok"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sentResetPasswordEmail embeds the new password in the rendered body")
    void sentResetPasswordEmail_embedsPassword() {
        buildService();
        emailService.sentResetPasswordEmail("user@example.com", "Nguyễn Văn A", "Temp#2026");

        verify(emailSender).send(
                eq("user@example.com"),
                contains("đặt lại mật khẩu"),
                argThat(content -> content.contains("Temp#2026") && content.contains("Nguyễn Văn A"))
        );
    }

    @Test
    @DisplayName("sentResetPasswordEmail rethrows when sender fails (used by admin flow that expects error)")
    void sentResetPasswordEmail_propagatesException() {
        buildService();
        doThrow(new RuntimeException("smtp-down")).when(emailSender).send(any(), any(), any());

        assertThatThrownBy(() -> emailService.sentResetPasswordEmail("user@example.com", "Name", "Pwd"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("sendNotificationEmail uses the provided subject verbatim and embeds the body")
    void sendNotificationEmail_usesProvidedSubject() {
        buildService();
        emailService.sendNotificationEmail("user@example.com", "My custom subject", "Hello\nworld");

        verify(emailSender).send(
                eq("user@example.com"),
                eq("My custom subject"),
                argThat(content -> content.contains("Hello") && content.contains("world"))
        );
    }

    @Test
    @DisplayName("sendNotificationEmail falls back to a default subject when caller passes null")
    void sendNotificationEmail_nullSubjectFallback() {
        buildService();
        emailService.sendNotificationEmail("user@example.com", null, "body");

        verify(emailSender).send(
                eq("user@example.com"),
                contains("Thông báo hệ thống"),
                any()
        );
    }

    @Test
    @DisplayName("sendInviteEmail embeds the invite link")
    void sendInviteEmail_containsInviteLink() {
        buildService();
        emailService.sendInviteEmail("invitee@example.com", "invite-tok-99");

        verify(emailSender).send(
                eq("invitee@example.com"),
                contains("Lời mời tham gia"),
                argThat(content -> content.contains("invite-tok-99"))
        );
    }

    @Test
    @DisplayName("sendInviteEmail swallows mail-sender exceptions silently")
    void sendInviteEmail_swallowsExceptions() {
        buildService();
        doThrow(new RuntimeException("smtp-down")).when(emailSender).send(any(), any(), any());

        assertThatCode(() -> emailService.sendInviteEmail("user@example.com", "tok"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Constructor accepts a blank frontend URL without throwing")
    void constructor_acceptsBlankUrl() {
        EmailSender sender = mock(EmailSender.class);

        EmailServiceImpl withNull = new EmailServiceImpl(sender, null);
        EmailServiceImpl withEmpty = new EmailServiceImpl(sender, "");
        EmailServiceImpl withSpaces = new EmailServiceImpl(sender, "   ");

        assertThat(withNull).isNotNull();
        assertThat(withEmpty).isNotNull();
        assertThat(withSpaces).isNotNull();
    }
}
