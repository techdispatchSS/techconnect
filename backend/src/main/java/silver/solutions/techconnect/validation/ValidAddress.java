package silver.solutions.techconnect.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.regex.Pattern;
import silver.solutions.techconnect.dto.request.common.AddressRequest;
import silver.solutions.techconnect.entity.Country;
import silver.solutions.techconnect.entity.Province;

/**
 * The country-dependent rules for an address (see {@link AddressRequest}). Failures are reported
 * against the field they concern, so the form can show the message beside the right input.
 */
@Documented
@Constraint(validatedBy = ValidAddress.Validator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidAddress {

    String message() default "is not a valid address";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidAddress, AddressRequest> {

        private static final Pattern SA_POSTCODE = Pattern.compile("\\d{4}");
        /** Letters, digits, space and hyphen: covers every postcode format in use, and no markup. */
        private static final Pattern POSTCODE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9 \\-]{0,11}");

        @Override
        public boolean isValid(AddressRequest address, ConstraintValidatorContext context) {
            if (address == null) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            boolean valid = true;
            boolean southAfrica = address.countryOrDefault() == Country.ZA;
            String postcode = address.postalCode() == null ? "" : address.postalCode().trim();

            if (southAfrica) {
                if (isBlank(address.suburb())) {
                    valid = fail(context, "suburb", "must not be blank");
                }
                if (address.province() != null && Arrays.stream(Province.values())
                        .noneMatch(p -> p.name().equals(address.province()))) {
                    valid = fail(context, "province", "must be one of South Africa's provinces");
                }
                if (!SA_POSTCODE.matcher(postcode).matches()) {
                    valid = fail(context, "postalCode", "must be a 4-digit postal code");
                }
            } else if (!postcode.isEmpty() && !POSTCODE.matcher(postcode).matches()) {
                valid = fail(context, "postalCode", "must be a valid postal code");
            }
            return valid;
        }

        private static boolean isBlank(String value) {
            return value == null || value.isBlank();
        }

        private static boolean fail(ConstraintValidatorContext context, String field, String message) {
            context.buildConstraintViolationWithTemplate(message)
                    .addPropertyNode(field)
                    .addConstraintViolation();
            return false;
        }
    }
}
