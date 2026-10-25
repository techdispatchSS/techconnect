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
 * One thing that happened to a ticket (FR-09). Insert-only: the repository has no update or
 * delete, and a database trigger refuses both.
 */
@Entity
@Table(name = "incident_events")
@Getter
@Setter
@NoArgsConstructor
public class IncidentEvent {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "incident_id", nullable = false, updatable = false)
    private UUID incidentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false)
    private IncidentEventType eventType;

    /** Null when the system acted (a Freshdesk sync, a dispatch lapsing). */
    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @Column(name = "dispatch_id", updatable = false)
    private UUID dispatchId;

    /** Written in words when it happened, so it stays true after a name changes. */
    @Column(nullable = false, updatable = false)
    private String summary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
