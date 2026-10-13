package silver.solutions.techconnect.service.implementation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import silver.solutions.techconnect.config.TechConnectProperties;
import silver.solutions.techconnect.entity.User;

class SmtpNotificationServiceTest {

    private JavaMailSender mailSender;
    private SmtpNotificationService service;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage())
                .thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
        var properties = new TechConnectProperties(
                null, "https://www.tech-connect.app", null, null, null,
                new TechConnectProperties.Mail("no-reply@tech-connect.app"),
                null, null, null, null);
        service = new SmtpNotificationService(mailSender, properties);
    }

    private static User user() {
        User user = mock(User.class);
        when(user.getEmail()).thenReturn("thabo@example.com");
        when(user.getName()).thenReturn("Thabo");
        return user;
    }

    private MimeMessage sent() throws Exception {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage message = captor.getValue();
        message.saveChanges();
        return message;
    }

    /** Every leaf of the message, in order, as {contentType, content}. */
    private static void leaves(Part part, List<String[]> into) throws Exception {
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                leaves(multipart.getBodyPart(i), into);
            }
        } else {
            into.add(new String[] {part.getContentType(), String.valueOf(part.getContent())});
        }
    }

    @Test
    void sendsTheSameWordsAsPlainTextAndAsBrandedHtml() throws Exception {
        service.sendPasswordReset(user(), "https://www.tech-connect.app/reset-password?token=abc");

        MimeMessage message = sent();
        assertThat(message.getSubject()).isEqualTo("Reset your TechConnect password");
        assertThat(message.getFrom()[0].toString()).isEqualTo("no-reply@tech-connect.app");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("thabo@example.com");

        List<String[]> parts = new ArrayList<>();
        leaves(message, parts);
        String text = parts.stream().filter(p -> p[0].startsWith("text/plain"))
                .map(p -> p[1]).findFirst().orElseThrow();
        String html = parts.stream().filter(p -> p[0].startsWith("text/html"))
                .map(p -> p[1]).findFirst().orElseThrow();

        assertThat(text).contains("Hi Thabo,").contains("reset-password?token=abc");
        assertThat(html)
                .contains("techconnect-email-logo.png")
                .contains("Hi Thabo,")
                .contains("<a href=\"https://www.tech-connect.app/reset-password?token=abc\"");
    }

    @Test
    void aFailingMailServerNeverFailsTheCaller() {
        doThrow(new MailSendException("relay down")).when(mailSender).send(any(MimeMessage.class));

        assertThatCode(() -> service.sendAccountDeactivated(user())).doesNotThrowAnyException();
    }
}
