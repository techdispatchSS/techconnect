package silver.solutions.techconnect.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A technician's on-site work against an accepted {@link Dispatch} (PRD FR-05, FR-06, FR-07).
 * {@code dispatchId} is nullable — a job can in principle exist without one (e.g. work assigned
 * outside the dispatch flow) even though every current write path always sets it.
 *
 * <p>Like every other entity here, related rows ({@code incidentId}, {@code dispatchId},
 * {@code technicianId}, {@code closedBy}) are plain UUID foreign keys rather than
 * {@code @ManyToOne} associations — consistent with {@link Dispatch#getIncidentId()}.
 */
@Entity
@Table(name = "jobs")
@Getter
@Setter
@NoArgsConstructor
public class Job {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Column(name = "dispatch_id")
    private UUID dispatchId;

    @Column(name = "technician_id", nullable = false)
    private UUID technicianId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status = JobStatus.EN_ROUTE;

    // FR-06: recorded automatically as the technician moves through the job, not entered by
    // hand — these three are set by the service layer as each status transition happens.
    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "arrived_at")
    private Instant arrivedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "resolution_notes")
    private String resolutionNotes;

    @Column(name = "root_cause")
    private String rootCause;

    private String recommendations;

    @Column(name = "labour_hours")
    private BigDecimal labourHours;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "closed_by")
    private UUID closedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closing_notes")
    private String closingNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
