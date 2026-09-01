package silver.solutions.techdispatch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * An immutable record of one administrative action (FR-09).
 *
 * <p>There is intentionally no setter-driven update path and no delete method on the
 * repository: FR-09 requires that audit records "shall not be editable or deletable by
 * any user".
 */
@Entity
@Table(name = "admin_audit_log")
@Getter
@Setter
@NoArgsConstructor
public class AdminAuditLog {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AdminAuditAction action;

    @Column(name = "target_user_id")
    private UUID targetUserId;

    // Read-only associations mapped over the same columns as the UUIDs above. They exist
    // solely so the audit search can filter on an actor's or target's name and email with a
    // join; writes still go through the UUID fields, which is why these are not insertable
    // or updatable. Lazy so listing never pulls user rows it does not display.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id", insertable = false, updatable = false)
    private User actor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id", insertable = false, updatable = false)
    private User target;

    /** Free-form JSON capturing the before/after of the change. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
