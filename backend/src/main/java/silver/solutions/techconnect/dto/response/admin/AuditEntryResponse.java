package silver.solutions.techconnect.dto.response.admin;

import java.time.Instant;
import java.util.UUID;
import silver.solutions.techconnect.entity.AdminAuditAction;

public record AuditEntryResponse(
        UUID id,
        AdminAuditAction action,
        UUID actorUserId,
        String actorName,
        UUID targetUserId,
        String targetName,
        String details,
        Instant createdAt) {}
