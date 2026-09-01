package silver.solutions.techdispatch.mapper;

import org.springframework.stereotype.Component;
import silver.solutions.techdispatch.dto.request.common.AddressRequest;
import silver.solutions.techdispatch.dto.response.common.AddressResponse;
import silver.solutions.techdispatch.entity.Address;

@Component
public class AddressMapper {

    public Address toEntity(AddressRequest request) {
        return new Address(
                request.street(),
                request.suburb(),
                request.city(),
                request.province(),
                request.postalCode());
    }

    /** Null in, null out — the bootstrap Manager has no address at all (see {@link
     * Address}'s javadoc). */
    public AddressResponse toAddressResponse(Address address) {
        if (address == null) {
            return null;
        }
        return new AddressResponse(
                address.getStreet(),
                address.getSuburb(),
                address.getCity(),
                address.getProvince(),
                address.getPostalCode());
    }
}
