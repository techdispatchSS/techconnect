package silver.solutions.techconnect.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Field-specific state for a {@link UserRole#TECHNICIAN} user. Provisioned at onboarding
 * with status {@link TechnicianStatus#OFFLINE}; the technician toggles it themselves (T-14)
 * and the dispatch engine reads it to build the availability list (FR-04).
 */
@Entity
@Table(name = "technician_profiles")
@Getter
@Setter
@NoArgsConstructor
public class TechnicianProfile {

    /** Shares the primary key with the owning user row. */
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TechnicianStatus status = TechnicianStatus.OFFLINE;

    @Column(name = "last_latitude")
    private BigDecimal lastLatitude;

    @Column(name = "last_longitude")
    private BigDecimal lastLongitude;

    @Column(name = "last_location_at")
    private Instant lastLocationAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public static TechnicianProfile forUser(UUID userId) {
        TechnicianProfile profile = new TechnicianProfile();
        profile.setUserId(userId);
        profile.setStatus(TechnicianStatus.OFFLINE);
        return profile;
    }
}
