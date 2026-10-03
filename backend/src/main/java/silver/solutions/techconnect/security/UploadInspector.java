package silver.solutions.techconnect.security;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import silver.solutions.techconnect.exception.ApiException;

/**
 * Decides whether an uploaded file is what it claims to be and safe enough to store and later
 * hand to a Manager. Nothing the client says about a file is trusted: the type is detected from
 * the bytes themselves, and the claimed content type and extension must agree with it.
 *
 * <p>What this does and does not do. It stops the common attacks: a script or executable
 * renamed to {@code .pdf}, a file whose declared type lies, an image with a payload appended
 * after its end marker, a decompression-bomb image, and PDFs carrying JavaScript or launch
 * actions. It is NOT a malware scanner. PDF active-content detection is a best-effort scan of
 * the raw bytes and cannot see inside compressed object streams, which is why downloads are
 * also served as attachments with a locked-down CSP and {@code nosniff} (see the controller),
 * and why a real antivirus scan is the recommended next step.
 */
@Component
public class UploadInspector {

    /** Smallest file that can plausibly be a real document; screens out empty/stub uploads. */
    private static final int MIN_BYTES = 100;

    /** Header-derived limits for images; nothing is decoded, but the Manager's browser will. */
    private static final long MAX_PIXELS = 100_000_000L;
    private static final int MAX_DIMENSION = 20_000;

    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] PNG_MAGIC = {
        (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A
    };
    private static final byte[] PNG_IEND = {
        0x00, 0x00, 0x00, 0x00, 'I', 'E', 'N', 'D', (byte) 0xAE, 0x42, 0x60, (byte) 0x82
    };

    /** PDF name objects that run code, open things, or reach out; any one rejects the file. */
    private static final Pattern PDF_ACTIVE_CONTENT = Pattern.compile(
            "/(JavaScript|JS|Launch|OpenAction|AA|RichMedia|XFA|SubmitForm|GoToR|GoToE"
                    + "|EmbeddedFiles?|ImportData|Encrypt)(?![A-Za-z0-9])");

    /** PDF lets any character of a name be written as #XX, e.g. /J#61vaScript. */
    private static final Pattern PDF_NAME_ESCAPE = Pattern.compile("#([0-9A-Fa-f]{2})");

    /** Verified, normalised facts about an upload. */
    public record Inspected(
            String contentType,
            String extension,
            String safeFilename,
            String sha256,
            long sizeBytes) {}

    private enum Kind {
        PDF("application/pdf", "pdf", Set.of("pdf")),
        PNG("image/png", "png", Set.of("png")),
        JPEG("image/jpeg", "jpg", Set.of("jpg", "jpeg"));

        final String mime;
        final String extension;
        final Set<String> allowedExtensions;

        Kind(String mime, String extension, Set<String> allowedExtensions) {
            this.mime = mime;
            this.extension = extension;
            this.allowedExtensions = allowedExtensions;
        }
    }

    public Inspected inspect(
            String claimedFilename, String claimedContentType, byte[] bytes, long maxBytes) {

        String label = displayLabel(claimedFilename);

        if (bytes == null || bytes.length < MIN_BYTES) {
            throw reject(label, "is empty or too small to be a real document.");
        }
        if (bytes.length > maxBytes) {
            throw new ApiException(HttpStatus.CONTENT_TOO_LARGE,
                    label + " is too large. Each file can be up to "
                            + (maxBytes / (1024 * 1024)) + " MB.");
        }

        Kind kind = detect(bytes);
        if (kind == null) {
            throw reject(label, "is not a PDF, JPG or PNG file.");
        }

        if (!claimedTypeMatches(claimedContentType, kind)) {
            throw reject(label, "does not match its file type. Upload a real PDF, JPG or PNG.");
        }
        String claimedExtension = extensionOf(claimedFilename);
        if (claimedExtension != null && !kind.allowedExtensions.contains(claimedExtension)) {
            throw reject(label, "has a file extension that does not match its contents.");
        }

        switch (kind) {
            case PDF -> checkPdf(bytes, label);
            case PNG -> {
                checkImage(bytes, "png", label);
                if (!endsWith(bytes, PNG_IEND)) {
                    throw reject(label, damagedImage());
                }
            }
            case JPEG -> {
                checkImage(bytes, "jpeg", label);
                if (!endsWithJpegMarker(bytes)) {
                    throw reject(label, damagedImage());
                }
            }
        }

        return new Inspected(
                kind.mime, kind.extension, safeFilename(claimedFilename, kind), sha256(bytes),
                bytes.length);
    }

    // --- detection -------------------------------------------------------------------------

    private Kind detect(byte[] bytes) {
        if (startsWith(bytes, PDF_MAGIC)) {
            return Kind.PDF;
        }
        if (startsWith(bytes, PNG_MAGIC)) {
            return Kind.PNG;
        }
        if (bytes.length > 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF) {
            return Kind.JPEG;
        }
        return null;
    }

    private boolean claimedTypeMatches(String claimed, Kind kind) {
        if (claimed == null) {
            return false;
        }
        String type = claimed.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return type.equals(kind.mime) || (kind == Kind.JPEG && type.equals("image/jpg"));
    }

    // --- per-format checks -----------------------------------------------------------------

    private void checkPdf(byte[] bytes, String label) {
        String text = new String(bytes, StandardCharsets.ISO_8859_1);

        // A PDF that was cut off, or has had something appended, is not what the applicant
        // exported — and a truncated one often won't open for the Manager anyway.
        int tailStart = Math.max(0, text.length() - 4096);
        if (!text.substring(tailStart).contains("%%EOF")) {
            throw reject(label, "looks incomplete or damaged. Re-export it and try again.");
        }

        String decoded = decodeNameEscapes(text);
        Matcher active = PDF_ACTIVE_CONTENT.matcher(decoded);
        if (active.find()) {
            throw reject(label, "contains scripts, embedded files or encryption, which we can't "
                    + "accept. Print it to a new PDF (or send a photo) and try again.");
        }
    }

    private String decodeNameEscapes(String text) {
        Matcher m = PDF_NAME_ESCAPE.matcher(text);
        StringBuilder out = new StringBuilder(text.length());
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(
                    String.valueOf((char) Integer.parseInt(m.group(1), 16))));
        }
        m.appendTail(out);
        return out.toString();
    }

    private void checkImage(byte[] bytes, String expectedFormat, String label) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw reject(label, "is not a readable image.");
            }
            ImageReader reader = readers.next();
            try {
                if (!expectedFormat.equalsIgnoreCase(reader.getFormatName())) {
                    throw reject(label, "does not match its file type.");
                }
                reader.setInput(in, true, true);
                // Dimensions come from the header; the pixels are never decoded here, so a
                // decompression-bomb image cannot be used to exhaust this server's memory.
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION
                        || (long) width * height > MAX_PIXELS) {
                    throw reject(label, "has unreasonable dimensions.");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException api) {
                throw api;
            }
            throw reject(label, "is not a readable image.");
        }
    }

    private String damagedImage() {
        return "looks damaged or has extra data attached. Re-save it as a plain JPG/PNG, or send "
                + "a PDF.";
    }

    private boolean endsWithJpegMarker(byte[] bytes) {
        int end = bytes.length;
        while (end > 2 && bytes[end - 1] == 0) {
            end--; // tolerate harmless zero padding
        }
        return end >= 2 && (bytes[end - 2] & 0xFF) == 0xFF && (bytes[end - 1] & 0xFF) == 0xD9;
    }

    // --- filenames -------------------------------------------------------------------------

    /**
     * A display-only name: path components, control characters and anything outside a plain
     * allow-list are removed, and the extension is forced to the detected type's. It is never
     * used to build a storage path (keys are random UUIDs), and is re-encoded when sent back in
     * a Content-Disposition header.
     */
    private String safeFilename(String claimed, Kind kind) {
        String name = claimed == null ? "" : claimed;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);

        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;

        base = base.replaceAll("[^\\p{L}\\p{N} ._()\\-]", "_")
                .replaceAll("_{2,}", "_")
                .replaceAll("\\s+", " ")
                .replaceAll("^[ ._]+", "")
                .trim();
        if (base.length() > 100) {
            base = base.substring(0, 100).trim();
        }
        if (base.isEmpty()) {
            base = "document";
        }
        return base + "." + kind.extension;
    }

    private String displayLabel(String claimedFilename) {
        String name = claimedFilename == null ? "" : claimedFilename;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("[^\\p{L}\\p{N} ._()\\-]", "_");
        if (name.length() > 60) {
            name = name.substring(0, 60) + "…";
        }
        return name.isBlank() ? "A file" : "\"" + name + "\"";
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return null;
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return null;
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    // --- helpers ---------------------------------------------------------------------------

    private ApiException reject(String label, String problem) {
        return ApiException.badRequest(label + " " + problem);
    }

    private boolean startsWith(byte[] bytes, byte[] prefix) {
        return bytes.length >= prefix.length
                && Arrays.equals(bytes, 0, prefix.length, prefix, 0, prefix.length);
    }

    private boolean endsWith(byte[] bytes, byte[] suffix) {
        return bytes.length >= suffix.length
                && Arrays.equals(bytes, bytes.length - suffix.length, bytes.length, suffix, 0,
                        suffix.length);
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JDK spec", e);
        }
    }
}
