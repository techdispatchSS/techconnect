package silver.solutions.techconnect.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import silver.solutions.techconnect.application.SaIds;

class SouthAfricanIdTest {

    @Test
    void acceptsAWellFormedNumber() {
        // Born 1 Jan 1990, citizen, with a computed check digit.
        assertThat(SouthAfricanId.isValid(SaIds.withCheckDigit("900101500008"))).isTrue();
        for (int i = 0; i < 200; i++) {
            assertThat(SouthAfricanId.isValid(SaIds.random())).isTrue();
        }
    }

    @Test
    void acceptsAPermanentResident() {
        assertThat(SouthAfricanId.isValid(SaIds.withCheckDigit("850615123118"))).isTrue();
    }

    @Test
    void rejectsAWrongCheckDigit() {
        String valid = SaIds.withCheckDigit("900101500008");
        char last = valid.charAt(12);
        String tampered = valid.substring(0, 12) + (char) ('0' + (last - '0' + 1) % 10);
        assertThat(SouthAfricanId.isValid(tampered)).isFalse();
    }

    @Test
    void rejectsTheWrongLengthOrNonDigits() {
        assertThat(SouthAfricanId.isValid(null)).isFalse();
        assertThat(SouthAfricanId.isValid("")).isFalse();
        assertThat(SouthAfricanId.isValid("90010150000")).isFalse();
        assertThat(SouthAfricanId.isValid("90010150000800")).isFalse();
        assertThat(SouthAfricanId.isValid("9001015000A08")).isFalse();
        assertThat(SouthAfricanId.isValid("9001 0150 0008")).isFalse();
        assertThat(SouthAfricanId.isValid("٩٠٠١٠١٥٠٠٠٠٨٠")).isFalse();
    }

    @Test
    void rejectsAnImpossibleBirthDate() {
        assertThat(SouthAfricanId.isValid(SaIds.withCheckDigit("901301500008"))).as("month 13").isFalse();
        assertThat(SouthAfricanId.isValid(SaIds.withCheckDigit("900230500008"))).as("30 Feb").isFalse();
        assertThat(SouthAfricanId.isValid(SaIds.withCheckDigit("900100500008"))).as("day 00").isFalse();
    }

    @Test
    void handlesLeapDaysAcrossCenturies() {
        // 29 Feb 2000 was a real date (and 1900 was not a leap year), so "000229" is valid.
        assertThat(SouthAfricanId.isValid(SaIds.withCheckDigit("000229500008"))).isTrue();
        // 29 Feb 2001 / 1901 never existed.
        assertThat(SouthAfricanId.isValid(SaIds.withCheckDigit("010229500008"))).isFalse();
    }

    @Test
    void rejectsAnInvalidCitizenshipDigit() {
        assertThat(SouthAfricanId.isValid(SaIds.withCheckDigit("900101500028"))).isFalse();
    }
}
