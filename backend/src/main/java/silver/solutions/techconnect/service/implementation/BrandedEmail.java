package silver.solutions.techconnect.service.implementation;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.web.util.HtmlUtils;

/**
 * The HTML alternative sent alongside every notification: the same words as the plain-text
 * message, set under the TechConnect logo. The text part is untouched, so a client that shows
 * only plain text (or a person who prefers it) sees exactly what it always did.
 *
 * <p>Deliberately plain markup with inline styles: mail clients strip {@code <style>} blocks and
 * ignore most modern CSS, and the logo is a PNG because several clients refuse SVG.
 */
final class BrandedEmail {

    /** Served by the frontend from {@code public/brand}, so it lives wherever the app does. */
    static final String LOGO_PATH = "/brand/techconnect-email-logo.png";

    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\"]+");

    private BrandedEmail() {}

    /**
     * Where the logo is fetched from. Tolerates a base URL configured without its scheme (the
     * deployed value once was just a bare CloudFront host), which would otherwise leave a broken
     * image in every message.
     */
    static String logoUrl(String appBaseUrl) {
        return pageUrl(appBaseUrl, LOGO_PATH);
    }

    /** A link to a page of the app (or one of its files), given the configured base address. */
    static String pageUrl(String appBaseUrl, String path) {
        String base = appBaseUrl == null ? "" : appBaseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (!base.contains("://")) {
            base = "https://" + base;
        }
        return base + path;
    }

    static String html(String plainText, String appBaseUrl) {
        String body = linkify(HtmlUtils.htmlEscape(plainText.strip()))
                .replace("\r\n", "\n")
                .replace("\n", "<br>")
                .replace("  ", "&nbsp; ");

        return """
                <!doctype html>
                <html lang="en">
                <body style="margin:0;padding:0;background:#f1f1f2;">
                <div style="max-width:560px;margin:0 auto;padding:24px 16px;\
                font-family:Arial,Helvetica,sans-serif;color:#26282c;">
                <div style="padding:0 0 18px 0;"><img src="%s" alt="TechConnect" width="190" \
                style="display:block;border:0;height:auto;max-width:100%%;"></div>
                <div style="background:#ffffff;border-top:3px solid #EE7611;padding:20px 22px;\
                font-size:15px;line-height:1.55;">%s</div>
                <p style="font-size:12px;color:#61656d;margin:16px 0 0 0;">\
                TechConnect &middot; field service dispatch</p>
                </div>
                </body>
                </html>
                """.formatted(HtmlUtils.htmlEscape(logoUrl(appBaseUrl)), body);
    }

    /** Makes bare URLs clickable. Trailing sentence punctuation is left outside the link. */
    private static String linkify(String escapedText) {
        Matcher matcher = URL.matcher(escapedText);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String url = matcher.group();
            String tail = "";
            while (!url.isEmpty() && ".,;:!?)".indexOf(url.charAt(url.length() - 1)) >= 0) {
                tail = url.charAt(url.length() - 1) + tail;
                url = url.substring(0, url.length() - 1);
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(
                    "<a href=\"" + url + "\" style=\"color:#b04e00;\">" + url + "</a>" + tail));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
