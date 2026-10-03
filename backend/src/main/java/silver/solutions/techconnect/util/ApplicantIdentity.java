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

    /** Digits only, with South Africa's +27 / 0027 prefix folded to the local leading 0. */
    public static String normalizePhone(String phone) {
        String digits = phone.replaceAll("\\D", "");
        if (digits.startsWith("0027")) {
            digits = digits.substring(4);
            return digits.startsWith("0") ? digits : "0" + digits;
        }
        if (digits.startsWith("27") && digits.length() == 11) {
            return "0" + digits.substring(2);
        }
        return digits;
    }
}
