package silver.solutions.techconnect.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One broadcast-or-assign attempt to staff an incident (PRD FR-04). A BROADCAST dispatch
 * fans out to every selected technician; the first ACCEPT wins and the rest lapse to
 * NO_RESPONSE once {@link #expiresAt} passes.
 *
 * <p>Like every other entity here, related rows ({@code incidentId}, {@code createdBy}) are
 * plain UUID foreign keys rather than {@code @ManyToOne} associations — consistent with
 * {@link User#getCreatedBy()} and {@link TechnicianProfile#getUserId()}.
 */
@Entity
@Table(name = "dispatches")
@Getter
@Setter
@NoArgsConstructor
public class Dispatch {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "dispatch_type", nullable = false)
    private DispatchType dispatchType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DispatchStatus status = DispatchStatus.PENDING;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "job_type")
    private String jobType;

    @Column(name = "required_skills")
    private String requiredSkills;

    @Column(name = "required_certifications")
    private String requiredCertifications;

    @Column(name = "sla_response")
    private String slaResponse;

    @Column(name = "site_contact")
    private String siteContact;

    @Column(name = "notes_for_technician")
    private String notesForTechnician;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
