package silver.solutions.techconnect.dto.response.admin;

import java.time.Instant;
import java.util.UUID;
import silver.solutions.techconnect.dto.response.common.AddressResponse;
import silver.solutions.techconnect.entity.TechnicianStatus;
import silver.solutions.techconnect.entity.UserRole;
import silver.solutions.techconnect.entity.UserStatus;

/** Built from a {@code User} entity by {@code mapper.UserMapper}, not here — this is a plain
 * data carrier. */
public record UserResponse(
        UUID id,
        String name,
        String email,
        String phone,
        /** Origin point for distance-based job matching (Phase 2). Manager-editable only. */
        AddressResponse address,
        UserRole role,
        UserStatus status,
        /** Populated only for technicians. */
        TechnicianStatus technicianStatus,
        boolean locked,
        Instant activatedAt,
        Instant createdAt) {}
