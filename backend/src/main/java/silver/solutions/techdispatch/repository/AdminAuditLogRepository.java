package silver.solutions.techdispatch.repository;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;
import silver.solutions.techdispatch.domain.AdminAuditLog;

/**
 * Deliberately extends the bare {@link Repository} marker rather than {@code JpaRepository}.
 *
 * <p>FR-09 requires that audit records "shall not be editable or deletable by any user".
 * Inheriting {@code JpaRepository} would hand every caller {@code delete}, {@code deleteAll}
 * and {@code saveAndFlush} on an audit table. Declaring only the operations that are
 * legitimate makes the immutability guarantee structural instead of a code-review convention.
 *
 * <p>{@link JpaSpecificationExecutor} is safe to add here: it contributes read operations
 * only, which is what the searchable audit view needs.
 */
public interface AdminAuditLogRepository
        extends Repository<AdminAuditLog, UUID>, JpaSpecificationExecutor<AdminAuditLog> {

    AdminAuditLog save(AdminAuditLog entry);

    Page<AdminAuditLog> findByTargetUserId(UUID targetUserId, Pageable pageable);
}
