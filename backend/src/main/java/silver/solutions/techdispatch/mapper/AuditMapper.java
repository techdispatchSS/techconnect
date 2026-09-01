package silver.solutions.techdispatch.mapper;

import org.springframework.stereotype.Component;
import silver.solutions.techdispatch.dto.response.admin.AuditEntryResponse;
import silver.solutions.techdispatch.entity.AdminAuditLog;

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
