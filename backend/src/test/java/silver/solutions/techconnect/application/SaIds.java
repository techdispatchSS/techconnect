package silver.solutions.techconnect.application;

import java.util.concurrent.ThreadLocalRandom;

/** Generates structurally valid South African ID numbers for tests (they belong to no one). */
public final class SaIds {

    private SaIds() {}

    /** A valid, random ID number born 1980-1999 (Luhn check digit computed). */
    public static String random() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        String yymmdd = "%02d%02d%02d".formatted(80 + r.nextInt(20), 1 + r.nextInt(12), 1 + r.nextInt(28));
        String sequence = "%04d".formatted(r.nextInt(10_000));
        return withCheckDigit(yymmdd + sequence + "08");
    }

    /** Appends the Luhn check digit to the first twelve digits. */
    public static String withCheckDigit(String first12) {
        int sum = 0;
        for (int i = 0; i < first12.length(); i++) {
            int digit = first12.charAt(first12.length() - 1 - i) - '0';
            if (i % 2 == 0) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
        }
        return first12 + (10 - sum % 10) % 10;
    }
}
