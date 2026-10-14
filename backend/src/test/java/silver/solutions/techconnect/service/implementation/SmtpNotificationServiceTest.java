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
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import silver.solutions.techconnect.config.TechConnectProperties;
import silver.solutions.techconnect.entity.IncidentPriority;
import silver.solutions.techconnect.entity.User;
import silver.solutions.techconnect.entity.UserRole;
import silver.solutions.techconnect.service.NotificationService;

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
                new TechConnectProperties.Mail(
                        "no-reply@tech-connect.app", "TechConnect", "support@tech-connect.app"),
                null, null, null, null);
        service = new SmtpNotificationService(mailSender, properties);
    }

    private static User user() {
        User user = mock(User.class);
        when(user.getEmail()).thenReturn("thabo@example.com");
        when(user.getName()).thenReturn("Thabo");
        when(user.getTimeZone()).thenReturn("Africa/Johannesburg");
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
        InternetAddress from = (InternetAddress) message.getFrom()[0];
        assertThat(from.getAddress()).isEqualTo("no-reply@tech-connect.app");
        assertThat(from.getPersonal()).isEqualTo("TechConnect");
        assertThat(((InternetAddress) message.getReplyTo()[0]).getAddress())
                .isEqualTo("support@tech-connect.app");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("thabo@example.com");

        List<String[]> parts = new ArrayList<>();
        leaves(message, parts);
        String text = parts.stream().filter(p -> p[0].startsWith("text/plain"))
                .map(p -> p[1]).findFirst().orElseThrow();
        String html = parts.stream().filter(p -> p[0].startsWith("text/html"))
                .map(p -> p[1]).findFirst().orElseThrow();

        assertThat(text).contains("Hi Thabo,").contains("reset-password?token=abc");
        assertThat(html)
                .contains("Tech-Connect")
                .contains("Hi Thabo,")
                .contains("<a href=\"https://www.tech-connect.app/reset-password?token=abc\"");
    }

    private static User userWithRole(UserRole role) {
        User user = user();
        when(user.getRole()).thenReturn(role);
        return user;
    }

    private String everythingSaid(MimeMessage message) throws Exception {
        List<String[]> parts = new ArrayList<>();
        leaves(message, parts);
        StringBuilder all = new StringBuilder();
        parts.forEach(p -> all.append(p[1]).append(System.lineSeparator()));
        return all.toString();
    }

    @Test
    void aTechniciansWelcomeEmailLinksToInstallingTheApp() throws Exception {
        service.sendActivationInvite(
                userWithRole(UserRole.TECHNICIAN),
                "https://www.tech-connect.app/activate?token=abc");

        String said = everythingSaid(sent());
        assertThat(said).contains("https://www.tech-connect.app/activate?token=abc");
        assertThat(said).contains("Install the app");
        assertThat(said).contains("https://www.tech-connect.app/install");
    }

    private static NotificationService.DispatchNotice notice(boolean reminder) {
        return new NotificationService.DispatchNotice(
                "INC-2C16638C",
                "Sihle Sithole",
                "13 Sloane Street, Bryanston, Epsom Downs, 2021",
                "Networking",
                IncidentPriority.HIGH,
                null,
                Instant.parse("2026-10-13T18:54:00Z"),
                reminder,
                UUID.fromString("11111111-2222-3333-4444-555555555555"));
    }

    @Test
    void aJobEmailIsADetailsGridWithAcceptAndDeclineButtons() throws Exception {
        service.sendDispatchNotice(user(), notice(false));

        MimeMessage message = sent();
        assertThat(message.getSubject()).isEqualTo("New job: INC-2C16638C · Sihle Sithole (high)");
        String all = everythingSaid(message);

        // the reference in the header, the details, and the time in a readable form
        assertThat(all).contains("INC-2C16638C").contains("Sihle Sithole").contains("Networking");
        assertThat(all).contains("13 Oct 2026, 20:54 SAST").doesNotContain("UTC");
        // the site is a map link rather than text a mail client guesses at
        assertThat(all).contains("https://www.google.com/maps/search/?api=1&amp;query=13+Sloane+Street");
        // priority, marked and coloured
        assertThat(all).contains("&#9679; High");
        // accept and decline, each carrying the dispatch and the answer
        assertThat(all).contains(">Accept job</a>").contains(">Decline</a>");
        assertThat(all).contains("dispatch=11111111-2222-3333-4444-555555555555&amp;respond=accept");
        assertThat(all).contains("dispatch=11111111-2222-3333-4444-555555555555&amp;respond=decline");
        assertThat(all).contains("You&#39;ll be asked to sign in to TechConnect.");
        assertThat(all).contains("waiting for your answer");
    }

    @Test
    void theLogoIsAttachedToTheMessageItself() throws Exception {
        service.sendDispatchNotice(user(), notice(false));

        MimeMessage message = sent();
        List<jakarta.mail.internet.MimeBodyPart> images = new ArrayList<>();
        collectImages(message, images);

        assertThat(images).hasSize(1);
        assertThat(images.getFirst().getContentType()).startsWith("image/png");
        assertThat(images.getFirst().getContentID()).isEqualTo("<tc-logo>");
        // a real PNG, not an empty placeholder
        byte[] bytes = images.getFirst().getInputStream().readAllBytes();
        assertThat(bytes).hasSizeGreaterThan(5_000);
        assertThat(bytes[1]).isEqualTo((byte) 'P');
        assertThat(everythingSaid(message)).contains("cid:tc-logo");
    }

    private static void collectImages(Part part, List<jakarta.mail.internet.MimeBodyPart> into)
            throws Exception {
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                collectImages(multipart.getBodyPart(i), into);
            }
        } else if (part.isMimeType("image/*")) {
            into.add((jakarta.mail.internet.MimeBodyPart) part);
        }
    }

    private String deadlineFor(String zone) throws Exception {
        User person = user();
        when(person.getTimeZone()).thenReturn(zone);
        service.sendDispatchNotice(person, notice(false));
        return everythingSaid(sent());
    }

    @Test
    void theDeadlineIsInTheRecipientsOwnZone() throws Exception {
        // 18:54 UTC, the same instant, said four ways
        assertThat(deadlineFor("Africa/Nairobi")).contains("13 Oct 2026, 21:54 EAT");
    }

    @Test
    void westAfricaReadsWatAndGhanaReadsGmt() throws Exception {
        assertThat(deadlineFor("Africa/Lagos")).contains("13 Oct 2026, 19:54 WAT");
    }

    @Test
    void ghanaIsOnGreenwichTime() throws Exception {
        assertThat(deadlineFor("Africa/Accra")).contains("13 Oct 2026, 18:54 GMT");
    }

    @Test
    void aZoneWithoutACommonNameIsGivenAsAnOffset() throws Exception {
        // Tripoli is UTC+2 all year but has no abbreviation people share
        assertThat(deadlineFor("Africa/Tripoli")).contains("13 Oct 2026, 20:54 UTC+2");
    }

    @Test
    void aPersonWithNoRecordedZoneIsGivenUtcRatherThanAGuess() throws Exception {
        assertThat(deadlineFor(null)).contains("13 Oct 2026, 18:54 UTC");
    }

    @Test
    void anUnreadableStoredZoneFallsBackToUtc() throws Exception {
        assertThat(deadlineFor("Not/AZone")).contains("13 Oct 2026, 18:54 UTC");
    }

    @Test
    void theDeadlineIsInSouthAfricanTimeEvenWhenItCrossesMidnight() throws Exception {
        var late = new NotificationService.DispatchNotice(
                "INC-1", "Acme", "1 Main Rd", "CCTV", IncidentPriority.LOW, null,
                Instant.parse("2026-10-13T22:30:00Z"), false, null);

        service.sendDispatchNotice(user(), late);

        // 22:30 UTC is half past midnight the next day in South Africa.
        assertThat(everythingSaid(sent())).contains("14 Oct 2026, 00:30 SAST");
    }

    @Test
    void aReminderSaysTheJobIsStillWaiting() throws Exception {
        service.sendDispatchNotice(user(), notice(true));

        MimeMessage message = sent();
        assertThat(message.getSubject()).startsWith("Reminder: job waiting for your answer");
        assertThat(everythingSaid(message)).contains("still waiting for an answer");
    }

    @Test
    void theJobEmailsPlainTextCarriesTheSameFacts() throws Exception {
        service.sendDispatchNotice(user(), notice(false));

        List<String[]> parts = new ArrayList<>();
        leaves(sent(), parts);
        String text = parts.stream().filter(p -> p[0].startsWith("text/plain"))
                .map(p -> p[1]).findFirst().orElseThrow();

        assertThat(text).contains("Ticket:").contains("INC-2C16638C");
        assertThat(text).contains("Client:").contains("Sihle Sithole");
        assertThat(text).contains("Site:").contains("13 Sloane Street");
        assertThat(text).contains("Priority:").contains("High");
        assertThat(text).contains("Respond by:").contains("13 Oct 2026, 20:54 SAST");
        assertThat(text).contains("Accept job: https://www.tech-connect.app/jobs?dispatch=");
    }

    @Test
    void otherRolesAreNotToldToInstallTheApp() throws Exception {
        service.sendActivationInvite(
                userWithRole(UserRole.CONTROLLER),
                "https://www.tech-connect.app/activate?token=abc");

        assertThat(everythingSaid(sent())).doesNotContain("/install");
    }

    @Test
    void leavesOutTheNameAndReplyToWhenTheyAreBlank() throws Exception {
        var plain = new TechConnectProperties(
                null, "https://www.tech-connect.app", null, null, null,
                new TechConnectProperties.Mail("no-reply@tech-connect.app", "", null),
                null, null, null, null);

        new SmtpNotificationService(mailSender, plain).sendAccountDeactivated(user());

        MimeMessage message = sent();
        InternetAddress from = (InternetAddress) message.getFrom()[0];
        assertThat(from.getAddress()).isEqualTo("no-reply@tech-connect.app");
        assertThat(from.getPersonal()).isNull();
        // With no Reply-To header, mail clients reply to the From address.
        assertThat(message.getHeader("Reply-To")).isNull();
    }

    @Test
    void aFailingMailServerNeverFailsTheCaller() {
        doThrow(new MailSendException("relay down")).when(mailSender).send(any(MimeMessage.class));

        assertThatCode(() -> service.sendAccountDeactivated(user())).doesNotThrowAnyException();
    }
}
