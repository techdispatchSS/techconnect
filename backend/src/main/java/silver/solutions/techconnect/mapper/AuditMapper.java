package silver.solutions.techconnect.mapper;

import org.springframework.stereotype.Component;
import silver.solutions.techconnect.dto.response.admin.AuditEntryResponse;
import silver.solutions.techconnect.entity.AdminAuditLog;

@Component
public class AuditMapper {

    public AuditEntryResponse toAuditEntryResponse(
            AdminAuditLog entry, String actorName, String targetName) {
        return new AuditEntryResponse(
                entry.getId(),
                entry.getAction(),
                entry.getActorUserId(),
                actorName,
                entry.getTargetUserId(),
                targetName,
                entry.getDetails(),
                entry.getCreatedAt());
    }
}
