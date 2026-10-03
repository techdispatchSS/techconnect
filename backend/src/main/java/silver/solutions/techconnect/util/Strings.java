package silver.solutions.techconnect.util;

/** Small string helpers with no other natural home. */
public final class Strings {

    private Strings() {}

    /** Optional text fields (phone, etc.) are stored as SQL {@code NULL} rather than an empty
     * or whitespace string, so "was this ever set" stays a clean, single check everywhere the
     * field is read. */
    public static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /**
     * Free text from an anonymous caller: trims, and removes control characters other than
     * newline and tab (NUL, escape sequences and the like have no business in a name or a
     * description, and can corrupt logs, emails and exports downstream).
     */
    public static String cleanText(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").trim();
    }
}
