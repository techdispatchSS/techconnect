package silver.solutions.techconnect.service.implementation;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import silver.solutions.techconnect.config.TechConnectProperties;
import silver.solutions.techconnect.entity.User;
import silver.solutions.techconnect.service.NotificationService;

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
    private final TechConnectProperties properties;

    public SmtpNotificationService(JavaMailSender mailSender, TechConnectProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void sendActivationInvite(User user, String activationUrl) {
        send(user, "Activate your TechConnect account", """
                Hi %s,

                An account has been created for you on TechConnect as a %s.

                Set your password here (the link is single-use and expires in 72 hours):
                %s

                If you were not expecting this, you can ignore this email.
                """.formatted(user.getName(), user.getRole().name().toLowerCase(), activationUrl));
    }

    @Override
    public void sendPasswordReset(User user, String resetUrl) {
        send(user, "Reset your TechConnect password", """
                Hi %s,

                A password reset was requested for your TechConnect account.

                Choose a new password here (the link is single-use and expires in 72 hours):
                %s

                If you did not request this, you can ignore this email. Your password is unchanged.
                """.formatted(user.getName(), resetUrl));
    }

    @Override
    public void sendAccountDeactivated(User user) {
        send(user, "Your TechConnect access has been removed", """
                Hi %s,

                Your TechConnect account has been deactivated, and you will no longer be able
                to sign in. Any session you had open has been ended.

                Your job history and records are retained. If you think this is a mistake,
                contact your manager.
                """.formatted(user.getName()));
    }

    @Override
    public void sendAccountReactivated(User user, String signInUrl) {
        send(user, "Your TechConnect access has been restored", """
                Hi %s,

                Your TechConnect account has been reactivated. You can sign in again here:
                %s

                Your existing password still works. If you no longer have it, use the
                'Forgot your password' link on the sign-in screen.
                """.formatted(user.getName(), signInUrl));
    }

    @Override
    public void sendApplicationReceived(String email, String firstName) {
        sendTo(email, "We received your TechConnect application", """
                Hi %s,

                Thanks for applying to join TechConnect as a technician. We have your details
                and your documents, and a manager will review them.

                You will get an email from us once a decision has been made. There is nothing
                more you need to do, and each person can only apply once.
                """.formatted(firstName));
    }

    @Override
    public void sendApplicationRejected(String email, String firstName) {
        sendTo(email, "Your TechConnect application", """
                Hi %s,

                Thank you for your interest in joining TechConnect as a technician. After
                reviewing your application, we are unable to offer you a place at this time.

                Please note that applications cannot be resubmitted.
                """.formatted(firstName));
    }

    @Override
    public void sendDispatchNotice(User technician, DispatchNotice notice) {
        String subject = (notice.reminder() ? "Reminder: job waiting for your answer — " : "New job: ")
                + notice.ticketRef() + " · " + notice.clientName()
                + (notice.priority() == null ? "" : " (" + notice.priority().name().toLowerCase() + ")");
        send(technician, subject, """
                Hi %s,

                %s

                Ticket:   %s
                Client:   %s
                Site:     %s
                Issue:    %s
                Priority: %s
                %s
                Respond by: %s UTC

                Sign in to TechConnect to accept or decline: %s
                """.formatted(
                technician.getName(),
                notice.reminder()
                        ? "A job sent to you is still waiting for an answer."
                        : "A new job has been sent to you and is waiting for your answer.",
                notice.ticketRef(),
                notice.clientName(),
                orDash(notice.siteAddress()),
                orDash(notice.issueType()),
                notice.priority() == null ? "—" : notice.priority().name().toLowerCase(),
                notice.notes() == null || notice.notes().isBlank() ? "" : "Notes:    " + notice.notes(),
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                        .withZone(java.time.ZoneOffset.UTC).format(notice.expiresAt()),
                properties.appBaseUrl()));
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private void send(User user, String subject, String body) {
        sendTo(user.getEmail(), subject, body);
    }

    private void sendTo(String to, String subject, String body) {
        // Logged unconditionally: this is what makes onboarding work before any mail
        // provider is configured, and what a developer reads on first run.
        log.info("Notification for {} — {}", to, subject);

        try {
            // Plain text and a branded HTML alternative; the text is exactly the message body.
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.mail().from());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, BrandedEmail.html(body, properties.appBaseUrl()));
            mailSender.send(message);
        } catch (MailException | MessagingException e) {
            log.warn("Could not deliver '{}' to {} — the action itself still succeeded. "
                    + "Use the activation URL from the API response or the log above.",
                    subject, to, e);
        }
    }
}
