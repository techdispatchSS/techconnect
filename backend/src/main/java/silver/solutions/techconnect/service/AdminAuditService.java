package silver.solutions.techconnect.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import silver.solutions.techconnect.dto.response.admin.AuditEntryResponse;
import silver.solutions.techconnect.dto.response.common.PageResponse;
import silver.solutions.techconnect.entity.AdminAuditAction;
import silver.solutions.techconnect.entity.AdminAuditLog;
import silver.solutions.techconnect.mapper.AuditMapper;
import silver.solutions.techconnect.repository.AdminAuditLogRepository;
import silver.solutions.techconnect.repository.AdminAuditLogSpecifications;
import silver.solutions.techconnect.repository.UserRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Writes the immutable record of administrative actions required by FR-09. */
@Service
public class AdminAuditService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuditService.class);

    private final AdminAuditLogRepository auditLog;
    private final UserRepository users;
    private final ObjectMapper objectMapper;
    private final AuditMapper auditMapper;

    public AdminAuditService(
            AdminAuditLogRepository auditLog,
            UserRepository users,
            ObjectMapper objectMapper,
            AuditMapper auditMapper) {
        this.auditLog = auditLog;
        this.users = users;
        this.objectMapper = objectMapper;
        this.auditMapper = auditMapper;
    }

    /**
     * Lists audit entries, filtered by any combination of target, action, free text and date
     * range. All filtering happens in the database — an audit trail is append-only and grows
     * without bound, so it can never be paged into the browser and filtered there.
     *
     * <p>Actor and target names are resolved in a single batch lookup so that rendering a
     * page of 20 entries costs two queries rather than forty-one.
     */
    @Transactional(readOnly = true)
    public PageResponse<AuditEntryResponse> list(
            UUID targetUserId,
            AdminAuditAction action,
            String query,
            Instant from,
            Instant to,
            Pageable pageable) {

        Page<AdminAuditLog> page = auditLog.findAll(
                AdminAuditLogSpecifications.matching(targetUserId, action, query, from, to),
                pageable);

        Set<UUID> referenced = new HashSet<>();
        page.getContent().forEach(entry -> {
            referenced.add(entry.getActorUserId());
            if (entry.getTargetUserId() != null) {
                referenced.add(entry.getTargetUserId());
            }
        });

        Map<UUID, String> names = new HashMap<>();
        if (!referenced.isEmpty()) {
            users.findAllById(referenced).forEach(user -> names.put(user.getId(), user.getName()));
        }

        return PageResponse.of(page, entry -> auditMapper.toAuditEntryResponse(
                entry, names.get(entry.getActorUserId()), names.get(entry.getTargetUserId())));
    }

    /**
     * Records one action.
     *
     * <p>{@link Propagation#MANDATORY} is the point of this method: it refuses to run
     * outside a caller's transaction. That makes "the audit row commits with the change it
     * describes" a structural guarantee rather than a convention — a rolled-back action can
     * never leave a phantom audit entry, and a committed one can never be missing its entry.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(
            UUID actorUserId, AdminAuditAction action, UUID targetUserId, Map<String, ?> details) {

        AdminAuditLog entry = new AdminAuditLog();
        entry.setActorUserId(actorUserId);
        entry.setAction(action);
        entry.setTargetUserId(targetUserId);
        entry.setDetails(serialise(details));
        auditLog.save(entry);
    }

    private String serialise(Map<String, ?> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JacksonException e) {
            // Never fail the underlying admin action over an unserialisable detail payload —
            // losing the "what changed" annotation is far better than losing the audit row.
            log.warn("Could not serialise audit details; recording the action without them", e);
            return null;
        }
    }
}
