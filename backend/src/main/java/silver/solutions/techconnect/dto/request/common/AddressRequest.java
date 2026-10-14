package silver.solutions.techconnect.dto.request.common;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import silver.solutions.techconnect.entity.Address;
import silver.solutions.techconnect.entity.Country;
import silver.solutions.techconnect.validation.ValidAddress;

/**
 * Shared between the admin portal, self-service profile editing and the public application — all
 * onboard or edit the same structured {@link Address}.
 *
 * <p>What is required depends on the country, which is why the cross-field rules live in
 * {@link ValidAddress} rather than on the fields: South Africa has a fixed list of provinces and
 * four-digit postcodes and expects a suburb; elsewhere the region is free text and the postcode
 * and suburb are optional (many African countries have no postcode system). Street and city are
 * always required, because distance-based job matching cannot work around a gap the way a person
 * reading a free-text address could.
 *
 * <p>{@code country} may be omitted, and then means South Africa, so a client written before
 * countries existed keeps working.
 */
@ValidAddress
public record AddressRequest(
        @NotBlank @Size(max = 255) String street,
        @Size(max = 120) String suburb,
        @NotBlank @Size(max = 120) String city,
        @NotBlank @Size(max = 120) String province,
        @Size(max = 12) String postalCode,
        Country country) {

    /** The country, defaulting when the client did not say. */
    public Country countryOrDefault() {
        return country == null ? Country.DEFAULT : country;
    }
}
