package silver.solutions.techdispatch.dto.response.common;

import silver.solutions.techdispatch.entity.Province;

/**
 * Conversion from {@link silver.solutions.techdispatch.entity.Address} lives in
 * {@code mapper.AddressMapper}, not here — this is a plain data carrier.
 */
public record AddressResponse(
        String street, String suburb, String city, Province province, String postalCode) {}
