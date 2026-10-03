package silver.solutions.techconnect.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import silver.solutions.techconnect.exception.ApiException;

class UploadInspectorTest {

    private static final long TEN_MB = 10L * 1024 * 1024;

    private final UploadInspector inspector = new UploadInspector();

    private byte[] pdf(String extra) {
        return ("%PDF-1.4\n% padding so the fixture clears the minimum size check .......\n"
                + "1 0 obj\n<< /Type /Catalog >>\nendobj\n" + extra
                + "\ntrailer\n<< /Root 1 0 R >>\nstartxref\n0\n%%EOF\n")
                .getBytes(StandardCharsets.ISO_8859_1);
    }

    private ApiException rejected(String name, String type, byte[] bytes) {
        var thrown = org.junit.jupiter.api.Assertions.assertThrows(
                ApiException.class, () -> inspector.inspect(name, type, bytes, TEN_MB));
        return thrown;
    }

    @Test
    void acceptsARealPdfAndReportsWhatItActuallyIs() {
        var result = inspector.inspect("My Cert.PDF", "application/pdf", pdf(""), TEN_MB);

        assertThat(result.contentType()).isEqualTo("application/pdf");
        assertThat(result.safeFilename()).isEqualTo("My Cert.pdf");
        assertThat(result.sha256()).hasSize(64);
    }

    @Test
    void detectsTheTypeFromTheBytesNotFromTheName() {
        // A genuine PDF with a .png name is refused rather than trusted either way.
        assertThat(rejected("scan.png", "application/pdf", pdf("")).getMessage())
                .contains("extension");
    }

    @Test
    void refusesEmptyAndTinyFiles() {
        assertThat(rejected("a.pdf", "application/pdf", new byte[0]).getMessage()).contains("empty");
        assertThat(rejected("a.pdf", "application/pdf", "%PDF-".getBytes()).getMessage())
                .contains("too small");
    }

    @Test
    void refusesOversizeFilesWith413() {
        byte[] big = pdf("x".repeat(2000));
        var thrown = org.junit.jupiter.api.Assertions.assertThrows(
                ApiException.class, () -> inspector.inspect("a.pdf", "application/pdf", big, 500));
        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
    }

    @Test
    void refusesExecutablesScriptsAndArchivesWhateverTheyAreCalled() {
        byte[] elf = new byte[400];
        elf[0] = 0x7f; elf[1] = 'E'; elf[2] = 'L'; elf[3] = 'F';
        byte[] mz = new byte[400];
        mz[0] = 'M'; mz[1] = 'Z';
        byte[] zip = new byte[400];
        zip[0] = 'P'; zip[1] = 'K'; zip[2] = 3; zip[3] = 4;
        byte[] html = "<html><script>alert(1)</script></html>".repeat(10).getBytes(StandardCharsets.UTF_8);

        for (byte[] payload : new byte[][] {elf, mz, zip, html}) {
            assertThat(rejected("cert.pdf", "application/pdf", payload).getMessage())
                    .contains("not a PDF, JPG or PNG");
        }
    }

    @Test
    void refusesPdfsWithActiveContentIncludingEscapedNames() {
        for (String active : new String[] {
            "<< /JavaScript (x) >>", "<< /JS (x) >>", "<< /Launch >>", "<< /OpenAction 1 0 R >>",
            "<< /AA << >> >>", "<< /EmbeddedFile >>", "<< /RichMedia >>", "<< /SubmitForm >>",
            "<< /Encrypt 9 0 R >>",
            "<< /J#61vaScript >>", "<< /#4AavaScript >>", "<< /Open#41ction >>"
        }) {
            assertThat(rejected("c.pdf", "application/pdf", pdf(active)).getMessage())
                    .as(active)
                    .contains("scripts, embedded files or encryption");
        }
    }

    @Test
    void aBenignPdfWithSimilarLookingWordsIsStillAccepted() {
        // "/JavaScripting" is a different name; "AAA" is not "/AA".
        var result = inspector.inspect("c.pdf", "application/pdf",
                pdf("<< /Title (JavaScript and AA basics) /Author /AAA >>"), TEN_MB);
        assertThat(result.contentType()).isEqualTo("application/pdf");
    }

    @Test
    void refusesATruncatedPdf() {
        byte[] truncated = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n".repeat(5)
                .getBytes(StandardCharsets.ISO_8859_1);
        assertThat(rejected("c.pdf", "application/pdf", truncated).getMessage())
                .contains("incomplete");
    }

    @Test
    void sanitisesHostileFilenamesAndNeverKeepsPathsOrControlCharacters() {
        var result = inspector.inspect(
                "..\\..\\windows\\<script>evil\u0000\n.pdf.pdf", "application/pdf", pdf(""), TEN_MB);

        assertThat(result.safeFilename())
                .doesNotContain("/").doesNotContain("\\").doesNotContain("<")
                .doesNotContain("\u0000").doesNotContain("\n")
                .endsWith(".pdf");
        assertThat(result.safeFilename().length()).isLessThan(120);
    }

    @Test
    void aNamelessUploadGetsASafeDefault() {
        assertThat(inspector.inspect(null, "application/pdf", pdf(""), TEN_MB).safeFilename())
                .isEqualTo("document.pdf");
    }

    @Test
    void errorMessagesNeverEchoRawFilenameMarkup() {
        var thrown = rejected("<img src=x onerror=alert(1)>.pdf", "application/pdf",
                "hello".repeat(40).getBytes());
        assertThat(thrown.getMessage()).doesNotContain("<").doesNotContain(">");
    }
}
