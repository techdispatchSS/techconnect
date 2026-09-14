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

/** One invited technician's outcome within a {@link Dispatch} — one row per invitee. */
@Entity
@Table(name = "dispatch_responses")
@Getter
@Setter
@NoArgsConstructor
public class DispatchResponse {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "dispatch_id", nullable = false)
    private UUID dispatchId;

    @Column(name = "technician_id", nullable = false)
    private UUID technicianId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DispatchResponseStatus response = DispatchResponseStatus.PENDING;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
