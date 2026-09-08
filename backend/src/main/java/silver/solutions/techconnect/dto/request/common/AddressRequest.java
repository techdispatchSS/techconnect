package silver.solutions.techconnect.dto.request.common;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import silver.solutions.techconnect.entity.Address;
import silver.solutions.techconnect.entity.Province;

/**
 * Shared between the admin portal and self-service profile editing — both onboard or edit the
 * same structured {@link Address}. Every field is required: a matching algorithm cannot work
 * around a gap the way a human reading a free-text address could (see {@link Address}).
 *
 * <p>Conversion to/from {@link Address} lives in {@code mapper.AddressMapper}, not here — this
 * is a plain data carrier with validation annotations only.
 */
public record AddressRequest(
        @NotBlank @Size(max = 255) String street,
        @NotBlank @Size(max = 120) String suburb,
        @NotBlank @Size(max = 120) String city,
        @NotNull Province province,
        @NotBlank @Pattern(regexp = "\\d{4}", message = "must be a 4-digit postal code")
                String postalCode) {}
