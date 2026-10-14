package silver.solutions.techconnect.service.implementation;

import java.util.ArrayList;
import java.util.List;
import org.springframework.web.util.HtmlUtils;

/**
 * One notification, described once and rendered twice: as the plain-text message and as the
 * branded HTML message that is sent alongside it. Both come from the same fields, so the two can
 * never say different things.
 *
 * <p>The HTML follows the "job assigned" design: a dark header with the TechConnect mark and the
 * ticket reference, an orange rule, the message, an optional grid of details, buttons, and a
 * quiet note. Markup is tables with inline styles, because mail clients strip {@code <style>}
 * blocks and ignore most modern CSS. Nothing depends on an image loading.
 *
 * <p>Every value is HTML-escaped; links are only emitted for {@code http(s)} addresses.
 */
record EmailContent(
        /** Shown on the right of the header (a ticket reference, say). Null for none. */
        String reference,
        /** What the reference is called in the plain-text version ("Ticket"). */
        String referenceLabel,
        String greeting,
        List<String> paragraphs,
        List<Detail> details,
        List<Button> buttons,
        /** A small, quiet line under the buttons. Null for none. */
        String note) {

    /** How a detail's value is coloured. */
    enum Tone {
        NORMAL,
        HIGH,
        MEDIUM,
        LOW
    }

    /**
     * A labelled fact. {@code wide} makes it take the full row; otherwise two details share one.
     * {@code href}, when present, makes the value a link.
     */
    record Detail(String label, String value, boolean wide, Tone tone, String href) {
        static Detail of(String label, String value) {
            return new Detail(label, value, false, Tone.NORMAL, null);
        }

        static Detail wide(String label, String value) {
            return new Detail(label, value, true, Tone.NORMAL, null);
        }
    }

    record Button(String label, String url, boolean primary) {
        static Button primary(String label, String url) {
            return new Button(label, url, true);
        }

        static Button secondary(String label, String url) {
            return new Button(label, url, false);
        }
    }

    static EmailContent simple(String greeting, List<String> paragraphs) {
        return new EmailContent(null, null, greeting, paragraphs, List.of(), List.of(), null);
    }

    EmailContent withButtons(Button... more) {
        return new EmailContent(
                reference, referenceLabel, greeting, paragraphs, details, List.of(more), note);
    }

    EmailContent withNote(String text) {
        return new EmailContent(
                reference, referenceLabel, greeting, paragraphs, details, buttons, text);
    }

    // ------------------------------------------------------------------ plain text

    String toPlainText() {
        List<String> blocks = new ArrayList<>();
        if (greeting != null && !greeting.isBlank()) {
            blocks.add(greeting);
        }
        blocks.addAll(paragraphs);

        List<String[]> facts = new ArrayList<>();
        if (reference != null && !reference.isBlank()) {
            facts.add(new String[] {referenceLabel == null ? "Reference" : referenceLabel, reference});
        }
        for (Detail d : details) {
            facts.add(new String[] {d.label(), d.value()});
        }
        if (!facts.isEmpty()) {
            int width = facts.stream().mapToInt(f -> f[0].length()).max().orElse(0) + 2;
            StringBuilder grid = new StringBuilder();
            for (String[] fact : facts) {
                grid.append(String.format("%-" + width + "s%s", fact[0] + ":", fact[1]))
                        .append("\n");
            }
            blocks.add(grid.toString().stripTrailing());
        }
        for (Button b : buttons) {
            blocks.add(b.label() + ": " + b.url());
        }
        if (note != null && !note.isBlank()) {
            blocks.add(note);
        }
        return String.join("\n\n", blocks) + "\n";
    }

    // ------------------------------------------------------------------ HTML

    /** The content id the logo is attached under; the HTML refers to it as {@code cid:tc-logo}. */
    static final String LOGO_CID = "tc-logo";

    private static final String ORANGE = "#f07f1d";
    private static final String INK = "#1a1c20";
    private static final String BODY = "#33363b";
    private static final String MUTED = "#6b7075";
    private static final String RULE = "#e3e4e6";
    private static final String FONT = "Arial,Helvetica,sans-serif";

    String toHtml() {
        StringBuilder html = new StringBuilder(4096);
        html.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                .append("<meta name=\"color-scheme\" content=\"light\"></head>")
                .append("<body style=\"margin:0;padding:0;background:#ededed;\">")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
                .append("style=\"background:#ededed;\"><tr><td align=\"center\" style=\"padding:20px 12px;\">")
                .append("<table role=\"presentation\" width=\"640\" cellpadding=\"0\" cellspacing=\"0\" ")
                .append("style=\"width:100%;max-width:640px;background:#ffffff;\">");

        header(html);

        html.append("<tr><td style=\"padding:32px 36px 36px 36px;font-family:").append(FONT)
                .append(";font-size:16px;line-height:1.55;color:").append(BODY).append(";\">");

        if (greeting != null && !greeting.isBlank()) {
            html.append("<p style=\"margin:0 0 6px 0;\">").append(esc(greeting)).append("</p>");
        }
        for (String paragraph : paragraphs) {
            html.append("<p style=\"margin:0 0 14px 0;\">").append(multiline(paragraph)).append("</p>");
        }
        if (!details.isEmpty()) {
            detailGrid(html);
        }
        if (!buttons.isEmpty()) {
            buttonRow(html);
        }
        if (note != null && !note.isBlank()) {
            html.append("<p style=\"margin:18px 0 0 0;font-size:14px;color:").append(MUTED).append(";\">")
                    .append(multiline(note)).append("</p>");
        }
        html.append("</td></tr>");

        html.append("<tr><td style=\"padding:0 36px 28px 36px;font-family:").append(FONT)
                .append(";font-size:12px;color:").append(MUTED)
                .append(";\">TechConnect &middot; field service dispatch</td></tr>")
                .append("</table></td></tr></table></body></html>");
        return html.toString();
    }

    private void header(StringBuilder html) {
        html.append("<tr><td style=\"background:").append(INK).append(";padding:22px 36px;")
                .append("border-bottom:5px solid ").append(ORANGE).append(";\">")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr>")
                // The real logo, attached to the message itself (not fetched from a web address), so
                // it shows even where remote images are blocked. The alt text is white on the dark
                // header for the clients that still decline to show it.
                .append("<td valign=\"middle\" style=\"font-family:").append(FONT)
                .append(";font-size:26px;font-weight:bold;color:#ffffff;\">")
                .append("<img src=\"cid:").append(LOGO_CID).append("\" alt=\"Tech-Connect\" width=\"214\" ")
                .append("style=\"display:block;border:0;outline:none;width:214px;max-width:100%;")
                .append("height:auto;color:#ffffff;font-size:26px;font-weight:bold;\"></td>");
        if (reference != null && !reference.isBlank()) {
            html.append("<td align=\"right\" valign=\"middle\" style=\"font-family:'Courier New',Courier,monospace;")
                    .append("font-size:13px;color:#a7abb1;\">").append(esc(reference)).append("</td>");
        }
        html.append("</tr></table></td></tr>");
    }

    private void detailGrid(StringBuilder html) {
        html.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
                .append("style=\"margin:18px 0 8px 0;border:1px solid ").append(RULE)
                .append(";background:#fafafa;\">");

        List<Detail> row = new ArrayList<>();
        for (Detail d : details) {
            if (d.wide()) {
                flush(html, row);
                detailRow(html, List.of(d));
            } else {
                row.add(d);
                if (row.size() == 2) {
                    flush(html, row);
                }
            }
        }
        flush(html, row);
        html.append("</table>");
    }

    private void flush(StringBuilder html, List<Detail> row) {
        if (!row.isEmpty()) {
            detailRow(html, new ArrayList<>(row));
            row.clear();
        }
    }

    private void detailRow(StringBuilder html, List<Detail> cells) {
        html.append("<tr>");
        for (int i = 0; i < cells.size(); i++) {
            Detail d = cells.get(i);
            boolean alone = cells.size() == 1;
            html.append("<td").append(alone ? " colspan=\"2\"" : " width=\"50%\"")
                    .append(" valign=\"top\" style=\"padding:14px 18px;border-top:1px solid ").append(RULE)
                    .append(!alone && i == 0 ? ";border-right:1px solid " + RULE : "")
                    .append(";font-family:").append(FONT).append(";\">")
                    .append("<div style=\"font-size:11px;letter-spacing:0.09em;text-transform:uppercase;")
                    .append("color:").append(MUTED).append(";\">").append(esc(d.label())).append("</div>")
                    .append("<div style=\"margin-top:3px;font-size:16px;font-weight:bold;color:")
                    .append(toneColour(d.tone())).append(";\">");

            String value = (d.tone() == Tone.NORMAL ? "" : "&#9679; ") + esc(d.value());
            if (isHttp(d.href())) {
                html.append("<a href=\"").append(esc(d.href())).append("\" style=\"color:").append(INK)
                        .append(";text-decoration:underline;\">").append(value).append("</a>");
            } else {
                html.append(value);
            }
            html.append("</div></td>");
        }
        html.append("</tr>");
    }

    private static String toneColour(Tone tone) {
        return switch (tone) {
            case HIGH -> "#b3261e";
            case MEDIUM -> "#b45309";
            case LOW -> "#4b5563";
            case NORMAL -> INK;
        };
    }

    private void buttonRow(StringBuilder html) {
        html.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:22px 0 0 0;\"><tr>");
        for (Button b : buttons) {
            if (!isHttp(b.url())) {
                continue;
            }
            String style = b.primary()
                    ? "background:" + ORANGE + ";border:1px solid " + ORANGE + ";color:" + INK + ";"
                    : "background:#ffffff;border:1px solid #cfd1d4;color:" + INK + ";";
            html.append("<td style=\"padding:0 12px 10px 0;\"><a href=\"").append(esc(b.url()))
                    .append("\" style=\"display:inline-block;").append(style)
                    .append("border-radius:6px;padding:15px 26px;font-family:").append(FONT)
                    .append(";font-size:17px;font-weight:bold;text-decoration:none;\">")
                    .append(esc(b.label())).append("</a></td>");
        }
        html.append("</tr></table>");
    }

    private static boolean isHttp(String url) {
        return url != null && (url.startsWith("https://") || url.startsWith("http://"));
    }

    private static String esc(String text) {
        return HtmlUtils.htmlEscape(text == null ? "" : text);
    }

    /** Escaped, with line breaks kept. */
    private static String multiline(String text) {
        return esc(text).replace("\r\n", "\n").replace("\n", "<br>");
    }
}
