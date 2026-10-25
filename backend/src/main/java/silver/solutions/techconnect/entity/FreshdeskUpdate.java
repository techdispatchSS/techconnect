package silver.solutions.techconnect.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One update for an incident's Freshdesk ticket, sent or (in demo mode) only recorded. See
 * V15__freshdesk_updates.sql. Ids are sequential so updates to a ticket go out in the order they
 * happened.
 */
@Entity
@Table(name = "freshdesk_updates")
@Getter
@Setter
@NoArgsConstructor
public class FreshdeskUpdate {

    /** What in TechConnect gave rise to the update. Each can be switched off on its own. */
    public enum Event {
        DISPATCH_SENT,
        DISPATCH_ACCEPTED,
        DISPATCH_EXPIRED,
        JOB_PROGRESS,
        REPORT_SUBMITTED,
        JOB_CLOSED
    }

    /** What it does to the ticket. */
    public enum Action {
        /** A private note: {@code POST /api/v2/tickets/{id}/notes}. */
        NOTE,
        /** Status to resolved: {@code PUT /api/v2/tickets/{id}}. */
        RESOLVE
    }

    public enum Delivery {
        /** Demo mode: recorded to show what would be sent, never sent. */
        DEMO,
        PENDING,
        SENT,
        /** Freshdesk refused it, or it kept failing; not retried. */
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Column(name = "ticket_id", nullable = false)
    private String ticketId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Event event;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Action action;

    /** The note's plain text; null for a status change. */
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Delivery delivery;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "sent_at")
    private Instant sentAt;
}
