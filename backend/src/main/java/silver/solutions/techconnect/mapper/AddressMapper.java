package silver.solutions.techconnect.mapper;

import java.util.Optional;
import org.springframework.stereotype.Component;
import silver.solutions.techconnect.dto.request.common.AddressRequest;
import silver.solutions.techconnect.dto.response.common.AddressResponse;
import silver.solutions.techconnect.entity.Address;

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

    /**
     * Empty in, empty out — the bootstrap Manager has no address at all (see {@link
     * Address}'s javadoc). {@link Optional} rather than a bare {@code null} return makes that
     * a fact callers must handle explicitly, not one they can forget to check.
     */
    public Optional<AddressResponse> toAddressResponse(Address address) {
        return Optional.ofNullable(address).map(a -> new AddressResponse(
                a.getStreet(),
                a.getSuburb(),
                a.getCity(),
                a.getProvince(),
                a.getPostalCode()));
    }
}
