package silver.solutions.techconnect.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import silver.solutions.techconnect.util.ApplicantIdentity;

/** Technicians can be based anywhere, so the list is the world's, not one continent's. */
class CountryTest {

    @Test
    void coversTheWorldNotOnlyAfrica() {
        assertThat(Country.values()).hasSizeGreaterThan(200);
        assertThat(Arrays.stream(Country.values()).map(Country::name))
                .contains("ZA", "KE", "NG", "GB", "US", "IN", "AE", "DE", "BR", "AU");
    }

    @Test
    void everyCountryIsARealIsoCodeWithADiallingCode() {
        var iso = java.util.Set.of(Locale.getISOCountries());
        for (Country country : Country.values()) {
            assertThat(iso).as(country.name()).contains(country.name());
            assertThat(country.dialCode()).as(country.name()).matches("\\d{1,3}");
            assertThat(country.displayName()).as(country.name()).isNotBlank();
        }
    }

    @Test
    void aCodeIsFoundWhicheverCaseItIsWritten() {
        assertThat(Country.fromCode("gb")).contains(Country.GB);
        assertThat(Country.fromCode("ZA")).contains(Country.ZA);
        assertThat(Country.fromCode("XX")).isEmpty();
    }

    @Test
    void aNumberFromAnywhereIsOneNumberHoweverItIsWritten() {
        String uk = ApplicantIdentity.normalizePhone("07911 123456", Country.GB.dialCode());

        assertThat(uk).isEqualTo("447911123456");
        assertThat(ApplicantIdentity.normalizePhone("+44 7911 123456", Country.GB.dialCode())).isEqualTo(uk);
        assertThat(ApplicantIdentity.normalizePhone("0044 7911 123456", Country.GB.dialCode())).isEqualTo(uk);

        // The United States and Canada share +1 and have no trunk 0.
        assertThat(ApplicantIdentity.normalizePhone("(212) 555-0147", Country.US.dialCode()))
                .isEqualTo("12125550147");
        assertThat(ApplicantIdentity.normalizePhone("+1 212 555 0147", Country.US.dialCode()))
                .isEqualTo("12125550147");
    }
}
