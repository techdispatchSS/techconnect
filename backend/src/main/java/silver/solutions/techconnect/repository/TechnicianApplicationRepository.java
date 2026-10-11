package silver.solutions.techconnect.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import silver.solutions.techconnect.entity.ApplicationStatus;
import silver.solutions.techconnect.entity.TechnicianApplication;

public interface TechnicianApplicationRepository
        extends JpaRepository<TechnicianApplication, UUID>,
                JpaSpecificationExecutor<TechnicianApplication> {

    /**
     * True if ANY of the three identifiers already belongs to an application of ANY status, which
     * is what makes a rejection permanent: changing just the email, or just the phone number, or
     * both, still leaves the ID number to give the applicant away.
     */
    boolean existsByEmailNormalizedOrPhoneNormalizedOrIdNumber(
            String emailNormalized, String phoneNormalized, String idNumber);

    long countByStatus(ApplicationStatus status);

    /**
     * Row-locked read for approve/reject, so two Managers deciding the same application at the
     * same moment serialise instead of both succeeding (which would provision two accounts or
     * overwrite a rejection).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from TechnicianApplication a where a.id = :id")
    Optional<TechnicianApplication> findByIdForUpdate(@Param("id") UUID id);
}
