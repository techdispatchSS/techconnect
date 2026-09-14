package silver.solutions.techconnect.dto.request.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import silver.solutions.techconnect.dto.request.common.AddressRequest;
import silver.solutions.techconnect.entity.UserRole;

public record UpdateUserRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 32) String phone,
        @NotNull @Valid AddressRequest address,
        @NotNull UserRole role) {}
