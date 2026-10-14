package silver.solutions.techconnect.service.implementation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import silver.solutions.techconnect.config.TechConnectProperties;
import silver.solutions.techconnect.entity.IncidentPriority;
import silver.solutions.techconnect.entity.User;
import silver.solutions.techconnect.entity.UserRole;
import silver.solutions.techconnect.service.NotificationService.DispatchNotice;

/**
 * Renders every email the app sends to {@code target/email-previews/*.html} so a person can open
 * them in a browser and look at the layout, and checks that each one produced both a plain-text
 * and an HTML part. The files are build output, not committed.
 */
class EmailPreviewTest {

    private static final String LOGO_BASE64;

    static {
        try (var in = EmailPreviewTest.class.getResourceAsStream(
                "/brand/techconnect-email-logo-light.png")) {
            LOGO_BASE64 = java.util.Base64.getEncoder().encodeToString(in.readAllBytes());
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static String htmlOf(Part part) throws Exception {
        if (part.isMimeType("text/html")) {
            return String.valueOf(part.getContent());
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                String found = htmlOf(multipart.getBodyPart(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static User user(UserRole role) {
        User user = mock(User.class);
        when(user.getEmail()).thenReturn("jack@example.com");
        when(user.getName()).thenReturn("Jack John");
        when(user.getRole()).thenReturn(role);
        return user;
    }

    @Test
    void everyEmailHasATextAndAnHtmlPartAndIsWrittenForPreview() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage())
                .thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
        var properties = new TechConnectProperties(
                null, "https://www.tech-connect.app", null, null, null,
                new TechConnectProperties.Mail(
                        "no-reply@tech-connect.app", "TechConnect", "support@tech-connect.app"),
                null, null, null, null);
        SmtpNotificationService service = new SmtpNotificationService(sender, properties);

        service.sendDispatchNotice(user(UserRole.TECHNICIAN), new DispatchNotice(
                "INC-2C16638C", "Sihle Sithole", "13 Sloane Street, Bryanston, Epsom Downs, 2021",
                "Networking", IncidentPriority.HIGH, "Gate code 1234. Ask for Sihle at reception.",
                Instant.parse("2026-10-13T18:54:00Z"), false, UUID.randomUUID()));
        service.sendActivationInvite(user(UserRole.TECHNICIAN), "https://www.tech-connect.app/activate?token=abc");
        service.sendActivationInvite(user(UserRole.CONTROLLER), "https://www.tech-connect.app/activate?token=abc");
        service.sendPasswordReset(user(UserRole.MANAGER), "https://www.tech-connect.app/reset-password?token=abc");
        service.sendAccountDeactivated(user(UserRole.TECHNICIAN));
        service.sendAccountReactivated(user(UserRole.TECHNICIAN), "https://www.tech-connect.app/login");
        service.sendApplicationReceived("jack@example.com", "Jack");
        service.sendApplicationRejected("jack@example.com", "Jack");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender, atLeastOnce()).send(captor.capture());
        List<MimeMessage> messages = captor.getAllValues();
        assertThat(messages).hasSize(8);

        Path out = Path.of("target", "email-previews");
        Files.createDirectories(out);
        String[] names = {
            "job-assigned", "activation-technician", "activation-controller", "password-reset",
            "account-deactivated", "account-reactivated", "application-received",
            "application-rejected"
        };
        for (int i = 0; i < messages.size(); i++) {
            MimeMessage message = messages.get(i);
            message.saveChanges();
            String html = htmlOf(message);
            assertThat(html).as(names[i]).isNotNull().contains("cid:tc-logo");
            // A browser cannot resolve cid: addresses, so the preview inlines the same image.
            Files.writeString(out.resolve(names[i] + ".html"),
                    html.replace("cid:tc-logo", "data:image/png;base64," + LOGO_BASE64));
        }
    }
}
