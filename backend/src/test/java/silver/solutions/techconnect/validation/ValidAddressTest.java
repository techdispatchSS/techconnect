package silver.solutions.techconnect.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import silver.solutions.techconnect.dto.request.common.AddressRequest;
import silver.solutions.techconnect.entity.Country;

class ValidAddressTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> problems(AddressRequest address) {
        return validator.validate(address).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    void aSouthAfricanAddressStillNeedsASuburbAProvinceFromTheListAndAFourDigitPostcode() {
        assertThat(problems(new AddressRequest(
                "1 Test St", "Umhlanga", "Durban", "KWAZULU_NATAL", "4001", Country.ZA))).isEmpty();

        assertThat(problems(new AddressRequest("1 Test St", "", "Durban", "KWAZULU_NATAL", "4001", Country.ZA)))
                .containsExactly("suburb");
        assertThat(problems(new AddressRequest("1 Test St", "Umhlanga", "Durban", "ATLANTIS", "4001", Country.ZA)))
                .containsExactly("province");
        assertThat(problems(new AddressRequest("1 Test St", "Umhlanga", "Durban", "KWAZULU_NATAL", "40", Country.ZA)))
                .containsExactly("postalCode");
        assertThat(problems(new AddressRequest("1 Test St", "Umhlanga", "Durban", "KWAZULU_NATAL", null, Country.ZA)))
                .containsExactly("postalCode");
    }

    @Test
    void anAddressWithNoCountryIsSouthAfrican() {
        assertThat(problems(new AddressRequest("1 Test St", "Umhlanga", "Durban", "NOT_A_PROVINCE", "4001", null)))
                .containsExactly("province");
    }

    @Test
    void elsewhereTheRegionIsFreeTextAndSuburbAndPostcodeAreOptional() {
        assertThat(problems(new AddressRequest("12 Moi Avenue", null, "Nairobi", "Nairobi County", null, Country.KE)))
                .isEmpty();
        assertThat(problems(new AddressRequest("5 Adeola Odeku", "Victoria Island", "Lagos", "Lagos State", "101241", Country.NG)))
                .isEmpty();
        assertThat(problems(new AddressRequest("1 Rue Liberté", "", "Dakar", "Dakar", "", Country.SN))).isEmpty();
    }

    @Test
    void streetCityAndRegionAreRequiredEverywhere() {
        assertThat(problems(new AddressRequest("", null, "", "", null, Country.KE)))
                .containsExactlyInAnyOrder("street", "city", "province");
    }

    @Test
    void aPostcodeOutsideSouthAfricaMustStillLookLikeOne() {
        assertThat(problems(new AddressRequest("1 St", null, "Accra", "Greater Accra", "<script>", Country.GH)))
                .containsExactly("postalCode");
        assertThat(problems(new AddressRequest("1 St", null, "Accra", "Greater Accra", "GA-123-4567", Country.GH)))
                .isEmpty();
    }
}
