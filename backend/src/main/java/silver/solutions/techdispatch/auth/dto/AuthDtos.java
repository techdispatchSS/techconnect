package silver.solutions.techdispatch.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import silver.solutions.techdispatch.domain.User;
import silver.solutions.techdispatch.domain.UserRole;

/**
 * Request and response bodies for PRD §8.1 and the activation/reset flow.
 *
 * <p>Password fields are capped at 72 characters because bcrypt silently ignores input
 * beyond 72 bytes — accepting a longer one would mean the tail of the user's password
 * does nothing, which is worse than rejecting it.
 */
public final class AuthDtos {

    public static final int PASSWORD_MIN = 12;
    public static final int PASSWORD_MAX = 72;

    private AuthDtos() {}

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {}

    /** §8.1 response 200 — the shape the frontend is written against. */
    public record LoginResponse(String token, String role, String userId, String name) {}

    public record ActivateRequest(
            @NotBlank String token,
            @NotBlank @Size(min = PASSWORD_MIN, max = PASSWORD_MAX) String password) {}

    public record ForgotPasswordRequest(@NotBlank @Email String email) {}

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = PASSWORD_MIN, max = PASSWORD_MAX) String password) {}

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = PASSWORD_MIN, max = PASSWORD_MAX) String newPassword) {}

    /** The signed-in user's own record, as returned by {@code GET /auth/me}. */
    public record ProfileResponse(
            UUID id,
            String email,
            String name,
            String phone,
            String address,
            UserRole role,
            /** False for every role but MANAGER — see {@code AuthService.updateProfile}. */
            boolean canEditAddress) {

        public static ProfileResponse from(User user) {
            return new ProfileResponse(
                    user.getId(),
                    user.getEmail(),
                    user.getName(),
                    user.getPhone(),
                    user.getAddress(),
                    user.getRole(),
                    user.getRole() == UserRole.MANAGER);
        }
    }

    /**
     * Self-service profile edit. Email and role are absent by design — changing either is an
     * administrative act that belongs in the audited admin endpoints, not in a user's own
     * settings dialog.
     */
    public record UpdateProfileRequest(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 32) String phone,
            @Size(max = 500) String address) {}
}
