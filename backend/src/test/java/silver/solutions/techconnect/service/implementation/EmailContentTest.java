package silver.solutions.techconnect.service.implementation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import silver.solutions.techconnect.service.implementation.EmailContent.Button;
import silver.solutions.techconnect.service.implementation.EmailContent.Detail;
import silver.solutions.techconnect.service.implementation.EmailContent.Tone;

class EmailContentTest {

    private static EmailContent job() {
        return new EmailContent(
                        "INC-2C16638C",
                        "Ticket",
                        "Hi Jack John,",
                        List.of("A new job has been sent to you and is waiting for your answer."),
                        List.of(
                                Detail.of("Client", "Sihle Sithole"),
                                Detail.of("Issue", "Networking"),
                                new Detail("Site", "13 Sloane Street", true, Tone.NORMAL,
                                        "https://maps.test/?q=13+Sloane"),
                                new Detail("Priority", "High", false, Tone.HIGH, null),
                                Detail.of("Respond by", "13 Oct 2026, 18:54 UTC")),
                        List.of(),
                        null)
                .withButtons(
                        Button.primary("Accept job", "https://app.test/jobs?respond=accept"),
                        Button.secondary("Decline", "https://app.test/jobs?respond=decline"))
                .withNote("You'll be asked to sign in to TechConnect.");
    }

    @Test
    void theHeaderCarriesTheRealLogoAndTheReference() {
        String html = job().toHtml();

        // the logo is the attached image, with readable alt text for clients that decline it
        assertThat(html)
                .contains("<img src=\"cid:tc-logo\"")
                .contains("alt=\"Tech-Connect\"")
                .contains("INC-2C16638C");
        // the orange rule under the header
        assertThat(html).contains("border-bottom:5px solid #f07f1d");
    }

    @Test
    void detailsAreAGridOfLabelsAndValuesWithTheSiteOnItsOwnRow() {
        String html = job().toHtml();

        assertThat(html)
                .contains(">Client</div>")
                .contains(">Sihle Sithole</div>")
                .contains(">Site</div>")
                .contains("colspan=\"2\"")
                .contains("<a href=\"https://maps.test/?q=13+Sloane\"")
                .contains("13 Oct 2026, 18:54 UTC");
        // priority is marked and coloured by how urgent it is
        assertThat(html).contains("&#9679; High").contains("color:#b3261e");
    }

    @Test
    void anOddDetailTakesTheWholeRowRatherThanLeavingAGap() {
        EmailContent content = new EmailContent(null, null, "Hi,", List.of(),
                List.of(Detail.of("A", "1"), Detail.of("B", "2"), Detail.of("C", "3")),
                List.of(), null);

        String html = content.toHtml();

        // two cells share the first row; the third is alone on the second
        assertThat(html.split("width=\"50%\"", -1)).hasSize(3);
        assertThat(html.split("colspan=\"2\"", -1)).hasSize(2);
    }

    @Test
    void buttonsArePrimaryAndSecondary() {
        String html = job().toHtml();

        assertThat(html)
                .contains(">Accept job</a>")
                .contains(">Decline</a>")
                .contains("background:#f07f1d")
                .contains("background:#ffffff");
        assertThat(html).contains("You&#39;ll be asked to sign in to TechConnect.");
    }

    @Test
    void escapesEverythingTheMessageCarries() {
        EmailContent content = new EmailContent(
                "<b>REF</b>", null, "Hi <script>alert(1)</script>,",
                List.of("Client: \"co\" & <img src=x onerror=alert(1)>"),
                List.of(Detail.of("<i>Label</i>", "<script>x</script>")),
                List.of(), null);

        String html = content.toHtml();

        assertThat(html)
                .doesNotContain("<script>")
                // the logo is the only image; an image tag in the message text stays inert text
                .doesNotContain("<img src=x")
                .contains("&lt;img src=x")
                .doesNotContain("<b>REF</b>")
                .contains("&lt;script&gt;")
                .contains("&amp;");
    }

    @Test
    void neverEmitsALinkThatIsNotHttp() {
        EmailContent content = new EmailContent(null, null, "Hi,", List.of(),
                List.of(new Detail("Site", "x", true, Tone.NORMAL, "javascript:alert(1)")),
                List.of(Button.primary("Click", "javascript:alert(1)")), null);

        String html = content.toHtml();

        assertThat(html).doesNotContain("javascript:").doesNotContain(">Click</a>");
    }

    @Test
    void thePlainTextSaysTheSameThingsInReadableForm() {
        String text = job().toPlainText();

        assertThat(text)
                .startsWith("Hi Jack John,")
                .contains("A new job has been sent to you and is waiting for your answer.")
                .contains("Ticket:")
                .contains("INC-2C16638C")
                .contains("Client:")
                .contains("Sihle Sithole")
                .contains("Respond by:")
                .contains("13 Oct 2026, 18:54 UTC")
                .contains("Accept job: https://app.test/jobs?respond=accept")
                .contains("Decline: https://app.test/jobs?respond=decline")
                .contains("You'll be asked to sign in to TechConnect.");
    }

    @Test
    void messagesWithoutDetailsOrButtonsAreJustTheWords() {
        EmailContent content = EmailContent.simple("Hi Thabo,", List.of("Your account has been deactivated."));

        String html = content.toHtml();

        assertThat(html).contains("Your account has been deactivated.");
        assertThat(html).doesNotContain("colspan").doesNotContain("display:inline-block");
        assertThat(content.toPlainText()).isEqualTo("Hi Thabo,\n\nYour account has been deactivated.\n");
    }
}
