package silver.solutions.techdispatch.dto.request.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import silver.solutions.techdispatch.dto.request.common.AddressRequest;
import silver.solutions.techdispatch.entity.UserRole;

/** Address is required here so every user onboarded through the portal has one. */
public record CreateUserRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Email @Size(max = 255) String email,
        @Size(max = 32) String phone,
        @NotNull @Valid AddressRequest address,
        @NotNull UserRole role) {}
