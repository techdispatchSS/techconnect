package silver.solutions.techconnect.service.implementation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BrandedEmailTest {

    @Test
    void carriesTheLogoFromTheFrontendsOwnAddress() {
        String html = BrandedEmail.html("Hello", "https://www.tech-connect.app/");

        assertThat(html)
                .contains("src=\"https://www.tech-connect.app/brand/techconnect-email-logo.png\"")
                .contains("alt=\"TechConnect\"");
    }

    @Test
    void toleratesABaseUrlConfiguredWithoutItsScheme() {
        assertThat(BrandedEmail.logoUrl("d1upytvz2jcmv0.cloudfront.net"))
                .isEqualTo("https://d1upytvz2jcmv0.cloudfront.net/brand/techconnect-email-logo.png");
        assertThat(BrandedEmail.logoUrl("http://localhost:4200"))
                .isEqualTo("http://localhost:4200/brand/techconnect-email-logo.png");
    }

    @Test
    void escapesWhateverTheMessageContains() {
        String html = BrandedEmail.html("Client: <script>alert(1)</script> & \"co\"", "https://x.test");

        assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;").contains("&amp;");
    }

    @Test
    void makesLinksClickableWithoutTakingTheFullStopWithThem() {
        String html = BrandedEmail.html(
                "Set your password here:\nhttps://app.test/activate?token=abc&x=1.\nThanks", "https://x.test");

        assertThat(html)
                .contains("<a href=\"https://app.test/activate?token=abc&amp;x=1\"")
                .contains(">https://app.test/activate?token=abc&amp;x=1</a>.");
    }

    @Test
    void keepsTheLineBreaksOfThePlainText() {
        String html = BrandedEmail.html("Line one\n\nLine two", "https://x.test");

        assertThat(html).contains("Line one<br><br>Line two");
    }
}
