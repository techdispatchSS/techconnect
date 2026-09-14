package silver.solutions.techconnect.dto.request.auth;

import static silver.solutions.techconnect.dto.request.auth.PasswordConstraints.PASSWORD_MAX;
import static silver.solutions.techconnect.dto.request.auth.PasswordConstraints.PASSWORD_MIN;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = PASSWORD_MIN, max = PASSWORD_MAX) String newPassword) {}
