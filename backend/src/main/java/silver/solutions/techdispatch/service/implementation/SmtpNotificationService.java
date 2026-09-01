package silver.solutions.techdispatch.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import silver.solutions.techdispatch.config.TechDispatchProperties;
import silver.solutions.techdispatch.domain.User;

/**
 * SMTP delivery. In local development this points at the Mailpit container from
 * docker-compose (web UI on :8025); in production it points at a real relay.
 *
 * <p>Sending is best-effort by design. FR-08 makes notifications a side effect of a
 * business action, not a precondition for one — a Manager onboarding a technician must
 * still succeed when the mail server is down, which is why the activation URL is also
 * logged and returned in the API response.
 */
@Service
public class SmtpNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(SmtpNotificationService.class);

    private final JavaMailSender mailSender;
    private final TechDispatchProperties properties;

    public SmtpNotificationService(JavaMailSender mailSender, TechDispatchProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void sendActivationInvite(User user, String activationUrl) {
        send(user, "Activate your TechDispatch account", """
                Hi %s,

                An account has been created for you on TechDispatch as a %s.

                Set your password here (the link is single-use and expires in 72 hours):
                %s

                If you were not expecting this, you can ignore this email.
                """.formatted(user.getName(), user.getRole().name().toLowerCase(), activationUrl));
    }

    @Override
    public void sendPasswordReset(User user, String resetUrl) {
        send(user, "Reset your TechDispatch password", """
                Hi %s,

                A password reset was requested for your TechDispatch account.

                Choose a new password here (the link is single-use and expires in 72 hours):
                %s

                If you did not request this, you can ignore this email. Your password is unchanged.
                """.formatted(user.getName(), resetUrl));
    }

    @Override
    public void sendAccountDeactivated(User user) {
        send(user, "Your TechDispatch access has been removed", """
                Hi %s,

                Your TechDispatch account has been deactivated, and you will no longer be able
                to sign in. Any session you had open has been ended.

                Your job history and records are retained. If you think this is a mistake,
                contact your manager.
                """.formatted(user.getName()));
    }

    @Override
    public void sendAccountReactivated(User user, String signInUrl) {
        send(user, "Your TechDispatch access has been restored", """
                Hi %s,

                Your TechDispatch account has been reactivated. You can sign in again here:
                %s

                Your existing password still works. If you no longer have it, use the
                'Forgot your password' link on the sign-in screen.
                """.formatted(user.getName(), signInUrl));
    }

    private void send(User user, String subject, String body) {
        // Logged unconditionally: this is what makes onboarding work before any mail
        // provider is configured, and what a developer reads on first run.
        log.info("Notification for {} — {}", user.getEmail(), subject);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mail().from());
        message.setTo(user.getEmail());
        message.setSubject(subject);
        message.setText(body);

        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.warn("Could not deliver '{}' to {} — the action itself still succeeded. "
                    + "Use the activation URL from the API response or the log above.",
                    subject, user.getEmail(), e);
        }
    }
}
