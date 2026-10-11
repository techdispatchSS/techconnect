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
import silver.solutions.techconnect.util.SouthAfricanId;

/** The value must be a well-formed 13-digit South African ID number (see {@link SouthAfricanId}). */
@Documented
@Constraint(validatedBy = ValidSouthAfricanId.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidSouthAfricanId {

    String message() default "must be a valid 13-digit South African ID number";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** Null/blank is left to {@code @NotBlank}, so each rule reports its own message. */
    class Validator implements ConstraintValidator<ValidSouthAfricanId, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || value.isBlank() || SouthAfricanId.isValid(value);
        }
    }
}
