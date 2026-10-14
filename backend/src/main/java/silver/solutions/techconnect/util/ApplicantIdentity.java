package silver.solutions.techconnect.util;

import java.util.Locale;
import java.util.Set;

/**
 * Collapses the many ways one person can write the same email address or phone number into a
 * single canonical form. The "one application per person, forever" rule (and with it, the
 * permanence of a rejection) is only as strong as this: if {@code a@x.com}, {@code A@x.com} and
 * {@code a+2@x.com} counted as three different applicants, a rejected applicant could reapply
 * simply by retyping their own address.
 *
 * <p>This raises the cost of evasion; it cannot stop a determined person with a second mailbox
 * and a second SIM. That is a limit of any anonymous form, not of the check.
 */
public final class ApplicantIdentity {

    private static final Set<String> GMAIL_DOMAINS = Set.of("gmail.com", "googlemail.com");

    private ApplicantIdentity() {}

    /** Lower-cased, "+tag" stripped, and Gmail's ignored dots removed. */
    public static String normalizeEmail(String email) {
        String value = email.trim().toLowerCase(Locale.ROOT);
        int at = value.lastIndexOf('@');
        if (at <= 0) {
            return value;
        }

        String local = value.substring(0, at);
        String domain = value.substring(at + 1);

        int plus = local.indexOf('+');
        if (plus > 0) {
            local = local.substring(0, plus);
        }
        if (GMAIL_DOMAINS.contains(domain)) {
            local = local.replace(".", "");
            domain = "gmail.com";
        }
        return local + "@" + domain;
    }

    /**
     * Digits only, in one canonical form whichever way the number was written, so a person cannot
     * apply again by retyping their own number: "+254 712 345 678", "0712 345 678" and
     * "00254712345678" are one number.
     *
     * <p>{@code dialCode} is the country the applicant says they are in (e.g. {@code "254"}); it
     * is what a number typed the local way, with a leading 0, is read against.
     *
     * <p>South African numbers keep their long-standing canonical form (a leading 0), so every
     * application already on record still matches.
     */
    public static String normalizePhone(String phone, String dialCode) {
        String raw = phone.trim();
        String digits = raw.replaceAll("\\D", "");

        String international;
        if (raw.startsWith("+")) {
            international = digits;
        } else if (digits.startsWith("00")) {
            international = digits.substring(2);
        } else if (digits.startsWith("0") && dialCode != null) {
            international = dialCode + digits.substring(1);
        } else if (dialCode != null && digits.startsWith(dialCode) && digits.length() > dialCode.length() + 6) {
            // already carries the country code, just without the plus
            international = digits;
        } else {
            international = dialCode != null ? dialCode + digits : digits;
        }

        if (international.startsWith("27") && international.length() == 11) {
            return "0" + international.substring(2);
        }
        return international;
    }

    /** As above for a South African applicant; kept for callers that have no country to hand. */
    public static String normalizePhone(String phone) {
        return normalizePhone(phone, "27");
    }
}
