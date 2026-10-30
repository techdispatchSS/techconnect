package silver.solutions.techconnect.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import silver.solutions.techconnect.entity.TechnicianProfile;

public interface TechnicianProfileRepository extends JpaRepository<TechnicianProfile, UUID> {

    /** Row-locked, so a technician's own toggle cannot interleave with another change to it. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from TechnicianProfile p where p.userId = :userId")
    Optional<TechnicianProfile> findByIdForUpdate(@Param("userId") UUID userId);
}
