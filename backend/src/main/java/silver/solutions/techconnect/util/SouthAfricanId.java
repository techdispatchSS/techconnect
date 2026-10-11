package silver.solutions.techconnect.util;

import java.time.DateTimeException;
import java.time.LocalDate;

/**
 * Structural validation of a South African ID number: {@code YYMMDD SSSS C A Z}.
 *
 * <p>This proves the number is <em>well formed</em> (right length, a real birth date, a valid
 * citizenship digit and the Luhn check digit). It cannot prove the number belongs to the person
 * entering it, or that it exists in Home Affairs' records; that needs a Manager looking at the
 * accompanying documents.
 */
public final class SouthAfricanId {

    private SouthAfricanId() {}

    public static boolean isValid(String id) {
        if (id == null || id.length() != 13) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            if (!Character.isDigit(id.charAt(i)) || id.charAt(i) > '9') {
                return false;
            }
        }
        return hasRealBirthDate(id) && hasValidCitizenshipDigit(id) && passesLuhn(id);
    }

    /** YYMMDD has no century, so it is valid if it is a real date, not in the future, in either. */
    private static boolean hasRealBirthDate(String id) {
        int yy = Integer.parseInt(id.substring(0, 2));
        int mm = Integer.parseInt(id.substring(2, 4));
        int dd = Integer.parseInt(id.substring(4, 6));
        LocalDate today = LocalDate.now();

        for (int century : new int[] {1900, 2000}) {
            try {
                LocalDate born = LocalDate.of(century + yy, mm, dd);
                if (!born.isAfter(today)) {
                    return true;
                }
            } catch (DateTimeException ignored) {
                // e.g. 29 February in a non-leap year of this century; try the other one
            }
        }
        return false;
    }

    /** 0 = citizen, 1 = permanent resident. */
    private static boolean hasValidCitizenshipDigit(String id) {
        char c = id.charAt(10);
        return c == '0' || c == '1';
    }

    private static boolean passesLuhn(String id) {
        int sum = 0;
        for (int i = 0; i < id.length(); i++) {
            int digit = id.charAt(id.length() - 1 - i) - '0';
            if (i % 2 == 1) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
        }
        return sum % 10 == 0;
    }
}
