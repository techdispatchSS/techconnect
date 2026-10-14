package silver.solutions.techconnect.service.implementation;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import silver.solutions.techconnect.config.TechConnectProperties;
import silver.solutions.techconnect.entity.User;
import silver.solutions.techconnect.entity.UserRole;
import silver.solutions.techconnect.service.NotificationService;
import silver.solutions.techconnect.service.implementation.EmailContent.Button;
import silver.solutions.techconnect.service.implementation.EmailContent.Detail;
import silver.solutions.techconnect.service.implementation.EmailContent.Tone;

/**
 * SMTP delivery. In local development this points at the Mailpit container from
 * docker-compose (web UI on :8025); in production it points at a real relay.
 *
 * <p>Every message is written once, as an {@link EmailContent}, and sent as both plain text and
 * the branded HTML layout, so the two always say the same thing.
 *
 * <p>Sending is best-effort by design. FR-08 makes notifications a side effect of a
 * business action, not a precondition for one — a Manager onboarding a technician must
 * still succeed when the mail server is down, which is why the activation URL is also
 * logged and returned in the API response.
 */
@Service
public class SmtpNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(SmtpNotificationService.class);

    /** The logo (light ink, for the dark header), packaged with the app. */
    private static final String LOGO_RESOURCE = "brand/techconnect-email-logo-light.png";

    /** "13 Oct 2026, 18:54 UTC". */
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm 'UTC'", Locale.ENGLISH)
                    .withZone(ZoneOffset.UTC);

    private final JavaMailSender mailSender;
    private final TechConnectProperties properties;

    public SmtpNotificationService(JavaMailSender mailSender, TechConnectProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void sendActivationInvite(User user, String activationUrl) {
        boolean technician = user.getRole() == UserRole.TECHNICIAN;
        List<String> paragraphs = new ArrayList<>();
        paragraphs.add("An account has been created for you on TechConnect as a "
                + user.getRole().name().toLowerCase() + ".");
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.primary("Set your password", activationUrl));

        // A technician works from their phone, so the email that welcomes them also says how to
        // put the app on it. (The application form itself deliberately doesn't offer install.)
        if (technician) {
            paragraphs.add("Once you are in, install the TechConnect app on your phone so jobs are"
                    + " one tap away.");
            buttons.add(Button.secondary("Install the app", page("/install")));
        }

        send(user, "Activate your TechConnect account",
                EmailContent.simple(hi(user.getName()), paragraphs)
                        .withButtons(buttons.toArray(Button[]::new))
                        .withNote("The password link is single-use and expires in 72 hours. "
                                + "If you were not expecting this, you can ignore this email."));
    }

    @Override
    public void sendPasswordReset(User user, String resetUrl) {
        send(user, "Reset your TechConnect password",
                EmailContent.simple(hi(user.getName()),
                                List.of("A password reset was requested for your TechConnect account."))
                        .withButtons(Button.primary("Choose a new password", resetUrl))
                        .withNote("The link is single-use and expires in 72 hours. If you did not "
                                + "request this, you can ignore this email. Your password is "
                                + "unchanged."));
    }

    @Override
    public void sendAccountDeactivated(User user) {
        send(user, "Your TechConnect access has been removed",
                EmailContent.simple(hi(user.getName()), List.of(
                        "Your TechConnect account has been deactivated, and you will no longer be"
                                + " able to sign in. Any session you had open has been ended.",
                        "Your job history and records are retained. If you think this is a "
                                + "mistake, contact your manager.")));
    }

    @Override
    public void sendAccountReactivated(User user, String signInUrl) {
        send(user, "Your TechConnect access has been restored",
                EmailContent.simple(hi(user.getName()), List.of(
                                "Your TechConnect account has been reactivated, and you can sign in"
                                        + " again."))
                        .withButtons(Button.primary("Sign in", signInUrl))
                        .withNote("Your existing password still works. If you no longer have it, "
                                + "use the 'Forgot your password' link on the sign-in screen."));
    }

    @Override
    public void sendApplicationReceived(String email, String firstName) {
        sendTo(email, "We received your TechConnect application",
                EmailContent.simple(hi(firstName), List.of(
                        "Thanks for applying to join TechConnect as a technician. We have your"
                                + " details and your documents, and a manager will review them.",
                        "You will get an email from us once a decision has been made. There is"
                                + " nothing more you need to do, and each person can only apply"
                                + " once.")));
    }

    @Override
    public void sendApplicationRejected(String email, String firstName) {
        sendTo(email, "Your TechConnect application",
                EmailContent.simple(hi(firstName), List.of(
                        "Thank you for your interest in joining TechConnect as a technician."
                                + " After reviewing your application, we are unable to offer you"
                                + " a place at this time.",
                        "Please note that applications cannot be resubmitted.")));
    }

    @Override
    public void sendDispatchNotice(User technician, DispatchNotice notice) {
        String subject = (notice.reminder() ? "Reminder: job waiting for your answer — " : "New job: ")
                + notice.ticketRef() + " · " + notice.clientName()
                + (notice.priority() == null ? "" : " (" + notice.priority().name().toLowerCase() + ")");

        List<Detail> details = new ArrayList<>();
        details.add(Detail.of("Client", orDash(notice.clientName())));
        details.add(Detail.of("Issue", orDash(notice.issueType())));
        details.add(new Detail("Site", orDash(notice.siteAddress()), true, Tone.NORMAL,
                hasText(notice.siteAddress()) ? mapsLink(notice.siteAddress()) : null));
        details.add(new Detail("Priority", notice.priority() == null ? "—" : label(notice.priority().name()),
                false, toneOf(notice), null));
        details.add(Detail.of("Respond by", WHEN.format(notice.expiresAt())));
        if (hasText(notice.notes())) {
            details.add(Detail.wide("Notes", notice.notes()));
        }

        // The technician app is not built yet, so both buttons land on the jobs page after sign-in;
        // the dispatch and the chosen answer travel in the address for it to pick up.
        String jobs = page("/jobs") + "?"
                + (notice.dispatchId() == null ? "" : "dispatch=" + notice.dispatchId() + "&");
        EmailContent content = new EmailContent(
                        notice.ticketRef(),
                        "Ticket",
                        hi(technician.getName()),
                        List.of(notice.reminder()
                                ? "A job sent to you is still waiting for an answer."
                                : "A new job has been sent to you and is waiting for your answer."),
                        details,
                        List.of(),
                        null)
                .withButtons(
                        Button.primary("Accept job", jobs + "respond=accept"),
                        Button.secondary("Decline", jobs + "respond=decline"))
                .withNote("You'll be asked to sign in to TechConnect.");

        send(technician, subject, content);
    }

    // ------------------------------------------------------------------ helpers

    private static String hi(String name) {
        return hasText(name) ? "Hi " + name.trim() + "," : "Hello,";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String orDash(String value) {
        return hasText(value) ? value : "—";
    }

    private static String label(String enumName) {
        return enumName.charAt(0) + enumName.substring(1).toLowerCase();
    }

    private static Tone toneOf(DispatchNotice notice) {
        if (notice.priority() == null) {
            return Tone.NORMAL;
        }
        return switch (notice.priority()) {
            case HIGH -> Tone.HIGH;
            case MEDIUM -> Tone.MEDIUM;
            case LOW -> Tone.LOW;
        };
    }

    private static String mapsLink(String address) {
        return "https://www.google.com/maps/search/?api=1&query="
                + URLEncoder.encode(address, StandardCharsets.UTF_8);
    }

    /**
     * A link to a page of the app. Tolerates a base address configured without its scheme (the
     * deployed value once was just a bare CloudFront host), which would otherwise leave a broken
     * link in every message.
     */
    private String page(String path) {
        String base = properties.appBaseUrl() == null ? "" : properties.appBaseUrl().trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (!base.contains("://")) {
            base = "https://" + base;
        }
        return base + path;
    }

    private void send(User user, String subject, EmailContent content) {
        sendTo(user.getEmail(), subject, content);
    }

    private void sendTo(String to, String subject, EmailContent content) {
        // Logged unconditionally: this is what makes onboarding work before any mail
        // provider is configured, and what a developer reads on first run.
        log.info("Notification for {} — {}", to, subject);

        try {
            // Plain text and the branded HTML layout, both written from the same content.
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            TechConnectProperties.Mail mail = properties.mail();
            if (mail.fromName() == null || mail.fromName().isBlank()) {
                helper.setFrom(mail.from());
            } else {
                helper.setFrom(mail.from(), mail.fromName());
            }
            if (mail.replyTo() != null && !mail.replyTo().isBlank()) {
                helper.setReplyTo(mail.replyTo());
            }
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content.toPlainText(), content.toHtml());
            // Must come after setText: the logo is attached to the HTML part it appears in.
            ClassPathResource logo = new ClassPathResource(LOGO_RESOURCE);
            if (logo.exists()) {
                helper.addInline(EmailContent.LOGO_CID, logo, "image/png");
            }
            mailSender.send(message);
        } catch (MailException | MessagingException | UnsupportedEncodingException e) {
            log.warn("Could not deliver '{}' to {} — the action itself still succeeded. "
                    + "Use the activation URL from the API response or the log above.",
                    subject, to, e);
        }
    }
}
