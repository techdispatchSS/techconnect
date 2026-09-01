package silver.solutions.techdispatch.dto.response.auth;

import java.util.UUID;
import silver.solutions.techdispatch.dto.response.common.AddressResponse;
import silver.solutions.techdispatch.entity.UserRole;

/**
 * The signed-in user's own record, as returned by {@code GET /auth/me}. Built from a
 * {@code User} entity by {@code mapper.UserMapper}, not here — this is a plain data carrier.
 */
public record ProfileResponse(
        UUID id,
        String email,
        String name,
        String phone,
        AddressResponse address,
        UserRole role,
        /** False for every role but MANAGER — see {@code AuthService.updateProfile}. */
        boolean canEditAddress) {}
