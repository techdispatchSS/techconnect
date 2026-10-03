package silver.solutions.techconnect.application;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;

/** Real, valid file bytes for upload tests, so a pass means the genuine article is accepted. */
final class TestFiles {

    private TestFiles() {}

    static byte[] pdf() {
        return pdfWith("");
    }

    /** A structurally plausible PDF (header, body, trailer, %%EOF) carrying extra raw text. */
    static byte[] pdfWith(String extra) {
        String body = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n"
                + "2 0 obj\n<< /Type /Pages /Kids [] /Count 0 >>\nendobj\n"
                + extra
                + "\ntrailer\n<< /Root 1 0 R /Size 3 >>\nstartxref\n0\n%%EOF\n";
        return body.getBytes(StandardCharsets.ISO_8859_1);
    }

    static byte[] png() {
        return image("png");
    }

    static byte[] jpeg() {
        return image("jpg");
    }

    static byte[] withTrailingBytes(byte[] original, String trailing) {
        byte[] extra = trailing.getBytes(StandardCharsets.ISO_8859_1);
        byte[] out = new byte[original.length + extra.length];
        System.arraycopy(original, 0, out, 0, original.length);
        System.arraycopy(extra, 0, out, original.length, extra.length);
        return out;
    }

    private static byte[] image(String format) {
        BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 40; x++) {
            for (int y = 0; y < 40; y++) {
                image.setRGB(x, y, (x * 6) << 16 | (y * 6) << 8 | 0x40);
            }
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
