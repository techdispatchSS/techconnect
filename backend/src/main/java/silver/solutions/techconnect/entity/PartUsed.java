package silver.solutions.techconnect.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One part/component consumed on a {@link Job} (FR-06). {@code serialNumber} is optional. */
@Entity
@Table(name = "parts_used")
@Getter
@Setter
@NoArgsConstructor
public class PartUsed {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "part_name", nullable = false)
    private String partName;

    @Column(nullable = false)
    private Integer quantity = 1;

    @Column(name = "serial_number")
    private String serialNumber;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
