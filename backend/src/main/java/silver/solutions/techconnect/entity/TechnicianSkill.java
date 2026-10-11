package silver.solutions.techconnect.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One skill or certification held by a {@link UserRole#TECHNICIAN}, used to rank dispatch
 * candidates against a job's required skills (FR-04).
 */
@Entity
@Table(name = "technician_skills")
@Getter
@Setter
@NoArgsConstructor
public class TechnicianSkill {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String skill;

    public TechnicianSkill(UUID userId, String skill) {
        this.userId = userId;
        this.skill = skill;
    }
}
