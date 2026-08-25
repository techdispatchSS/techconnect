package silver.solutions.techdispatch.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import silver.solutions.techdispatch.domain.AdminAuditAction;
import silver.solutions.techdispatch.domain.TechnicianStatus;
import silver.solutions.techdispatch.domain.User;
import silver.solutions.techdispatch.domain.UserRole;
import silver.solutions.techdispatch.domain.UserStatus;

/** Request and response bodies for the admin portal (M-06). */
public final class AdminDtos {

    private AdminDtos() {}

    /** Address is required here so every user onboarded through the portal has one. */
    public record CreateUserRequest(
            @NotBlank @Size(max = 255) String name,
            @NotBlank @Email @Size(max = 255) String email,
            @Size(max = 32) String phone,
            @NotBlank @Size(max = 500) String address,
            @NotNull UserRole role) {}

    public record UpdateUserRequest(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 32) String phone,
            @Size(max = 500) String address,
            @NotNull UserRole role) {}

    public record UserResponse(
            UUID id,
            String name,
            String email,
            String phone,
            /** Origin point for distance-based job matching (Phase 2). Manager-editable only. */
            String address,
            UserRole role,
            UserStatus status,
            /** Populated only for technicians. */
            TechnicianStatus technicianStatus,
            boolean locked,
            Instant activatedAt,
            Instant createdAt) {

        public static UserResponse from(User user, TechnicianStatus technicianStatus) {
            return new UserResponse(
                    user.getId(),
                    user.getName(),
                    user.getEmail(),
                    user.getPhone(),
                    user.getAddress(),
                    user.getRole(),
                    user.getStatus(),
                    technicianStatus,
                    user.isLocked(),
                    user.getActivatedAt(),
                    user.getCreatedAt());
        }
    }

    /**
     * The activation URL is returned to the Manager, not just emailed, so onboarding works
     * before any mail provider is configured and when a technician's inbox is unreachable.
     * It is single-use and expires in 72 hours.
     */
    public record CreateUserResponse(UserResponse user, String activationUrl) {}

    public record InviteResponse(String activationUrl) {}

    public record AuditEntryResponse(
            UUID id,
            AdminAuditAction action,
            UUID actorUserId,
            String actorName,
            UUID targetUserId,
            String targetName,
            String details,
            Instant createdAt) {}
}
