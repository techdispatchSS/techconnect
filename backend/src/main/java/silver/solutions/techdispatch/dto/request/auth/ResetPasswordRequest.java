package silver.solutions.techdispatch.dto.request.auth;

import static silver.solutions.techdispatch.dto.request.auth.PasswordConstraints.PASSWORD_MAX;
import static silver.solutions.techdispatch.dto.request.auth.PasswordConstraints.PASSWORD_MIN;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank @Size(min = PASSWORD_MIN, max = PASSWORD_MAX) String password) {}
