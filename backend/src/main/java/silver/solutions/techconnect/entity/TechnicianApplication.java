package silver.solutions.techconnect.entity;

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
 * A prospective technician's application, submitted through the public apply page with no
 * account. Kept entirely separate from {@link User}: an applicant has no password, role or
 * session, and a rejected applicant must never become a login-capable row.
 *
 * <p>Rows are never deleted. A {@link ApplicationStatus#REJECTED} record is what stops the same
 * person applying again, enforced by unique indexes on {@link #emailNormalized} and
 * {@link #phoneNormalized} rather than by application code that a race or a bug could skip.
 */
@Entity
@Table(name = "technician_applications")
@Getter
@Setter
@NoArgsConstructor
public class TechnicianApplication {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(nullable = false)
    private String email;

    @Column(name = "email_normalized", nullable = false)
    private String emailNormalized;

    @Column(nullable = false)
    private String phone;

    @Column(name = "phone_normalized", nullable = false)
    private String phoneNormalized;

    /**
     * South African ID number: personal information, shown only to Managers reviewing the
     * application and never logged. Also an identity key for the one-application-per-person rule.
     * Null only on rows created before the column existed.
     */
    @Column(name = "id_number")
    private String idNumber;

    @Embedded
    private Address address;

    @Column(name = "experience_summary", nullable = false)
    private String experienceSummary;

    private String skills;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status = ApplicationStatus.PENDING;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_reason")
    private String decisionReason;

    /** The account provisioned on approval; null until then, and always null if rejected. */
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public String fullName() {
        return (firstName + " " + lastName).trim();
    }
}
