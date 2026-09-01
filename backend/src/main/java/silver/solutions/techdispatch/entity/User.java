package silver.solutions.techdispatch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The identity record for every human who logs in (PRD FR-01). This table is the system's
 * only user directory — there is no external IdP.
 *
 * <p>Rows are never deleted. Offboarding sets {@link UserStatus#DISABLED}, because the
 * jobs, job_status_history and dispatch_responses tables hold foreign keys here and FR-09
 * forbids destroying audit history.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String email;

    /**
     * Null until the user activates. The Manager who creates the account never sets or
     * sees a password; the user chooses their own via a single-use activation link.
     */
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(nullable = false)
    private String name;

    private String phone;

    /**
     * Structured postal address. For technicians this is the origin point for distance-based
     * job matching (Phase 2), which is why only a Manager may change it — a technician editing
     * their own address would be editing dispatch logic.
     */
    @Embedded
    private Address address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.PENDING_ACTIVATION;

    /**
     * Incremented whenever every existing session for this user must stop working:
     * offboarding, a password change, or a password reset. Issued JWTs carry the value
     * they were minted with, and the auth filter rejects any token whose value is stale.
     * Without this, an 8-hour token would outlive the Manager's decision to revoke access.
     */
    @Column(name = "token_version", nullable = false)
    private int tokenVersion = 0;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "activated_at")
    private Instant activatedAt;

    /** Null for the bootstrap Manager, which no one created through the portal. */
    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /** True while a brute-force lockout is still in effect (§9.2). */
    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }
}
