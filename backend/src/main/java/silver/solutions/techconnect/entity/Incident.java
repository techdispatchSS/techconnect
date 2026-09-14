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
 * A logged fault (PRD FR-02, FR-03) — sourced from Freshdesk or logged directly by a
 * Controller when a client calls in. This is the queue the controller dashboard works from.
 */
@Entity
@Table(name = "incidents")
@Getter
@Setter
@NoArgsConstructor
public class Incident {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "freshdesk_ticket_id")
    private String freshdeskTicketId;

    @Column(name = "client_name", nullable = false)
    private String clientName;

    @Column(name = "site_address")
    private String siteAddress;

    @Column(name = "site_latitude")
    private BigDecimal siteLatitude;

    @Column(name = "site_longitude")
    private BigDecimal siteLongitude;

    @Column(name = "contact_name")
    private String contactName;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "issue_type")
    private String issueType;

    @Enumerated(EnumType.STRING)
    private IncidentPriority priority;

    private String sla;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentStatus status = IncidentStatus.NEW;

    private String description;

    @Column(name = "infrastructure_type")
    private String infrastructureType;

    @Column(name = "inventory_notes")
    private String inventoryNotes;

    @Column(name = "additional_notes")
    private String additionalNotes;

    @Column(name = "sla_due_at")
    private Instant slaDueAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public boolean isOverdue() {
        return status != IncidentStatus.CLOSED && slaDueAt != null && slaDueAt.isBefore(Instant.now());
    }
}
