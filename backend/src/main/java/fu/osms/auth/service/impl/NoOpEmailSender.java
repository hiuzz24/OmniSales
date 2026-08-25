package fu.osms.auth.service.impl;

import fu.osms.auth.service.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * No-op email sender used in the CI profile.
 *
 * The CI profile (SPRING_PROFILES_ACTIVE=ci) runs without real mail infrastructure.
 * Other profiles (dev, default → {@link DevSmtpEmailSender}, render → {@link ProdApiEmailSender})
 * are not active here, so without this bean Spring cannot autowire EmailSender into
 * EmailServiceImpl.
 *
 * This bean simply logs the email instead of sending it, so any code path that
 * triggers an email during E2E tests will not fail the build.
 */
@Service
@Profile("ci")
public class NoOpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(NoOpEmailSender.class);

    @Override
    public void send(String to, String subject, String htmlContent) {
        log.info("[CI/NoOp] Would send email to={} subject={} bodyLength={}",
                to, subject, htmlContent == null ? 0 : htmlContent.length());
    }

    @Override
    public void send(String to, String subject, String content, boolean isHtml) {
        log.info("[CI/NoOp] Would send email to={} subject={} isHtml={} bodyLength={}",
                to, subject, isHtml, content == null ? 0 : content.length());
    }
}
