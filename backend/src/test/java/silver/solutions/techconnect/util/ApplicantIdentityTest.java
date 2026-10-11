package silver.solutions.techconnect.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApplicantIdentityTest {

    @Test
    void foldsCaseAndPlusTagsIntoOneEmail() {
        assertThat(ApplicantIdentity.normalizeEmail("Nomsa@Example.com"))
                .isEqualTo(ApplicantIdentity.normalizeEmail("nomsa+jobs@example.com"))
                .isEqualTo("nomsa@example.com");
    }

    @Test
    void ignoresGmailDotsAndTheGooglemailAlias() {
        assertThat(ApplicantIdentity.normalizeEmail("n.o.m.s.a@gmail.com")).isEqualTo("nomsa@gmail.com");
        assertThat(ApplicantIdentity.normalizeEmail("nomsa@googlemail.com")).isEqualTo("nomsa@gmail.com");
    }

    @Test
    void doesNotStripDotsOnOtherProviders() {
        // Dots are significant elsewhere: a.b@corp.co.za and ab@corp.co.za are different people.
        assertThat(ApplicantIdentity.normalizeEmail("a.b@corp.co.za")).isEqualTo("a.b@corp.co.za");
    }

    @Test
    void foldsEverySouthAfricanPhoneSpellingIntoOne() {
        String local = "0821234567";
        assertThat(ApplicantIdentity.normalizePhone("+27 82 123 4567")).isEqualTo(local);
        assertThat(ApplicantIdentity.normalizePhone("0027821234567")).isEqualTo(local);
        assertThat(ApplicantIdentity.normalizePhone("082-123-4567")).isEqualTo(local);
        assertThat(ApplicantIdentity.normalizePhone("(082) 123 4567")).isEqualTo(local);
        assertThat(ApplicantIdentity.normalizePhone("27821234567")).isEqualTo(local);
    }
}
