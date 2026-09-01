package silver.solutions.techdispatch.dto.request.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import silver.solutions.techdispatch.dto.request.common.AddressRequest;

/**
 * Self-service profile edit. Email and role are absent by design — changing either is an
 * administrative act that belongs in the audited admin endpoints, not in a user's own
 * settings dialog.
 *
 * <p>Address is optional here, unlike the admin portal's requests: every role reaches this
 * endpoint to edit their name or phone, but only a MANAGER's address change is ever honoured
 * (see {@code AuthService.updateProfile}), so a Controller or Technician must not be forced to
 * submit a fully valid address just to change their phone number.
 */
public record UpdateProfileRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 32) String phone,
        @Valid AddressRequest address) {}
