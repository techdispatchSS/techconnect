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
     *
     * <p>The ID number is compared within its country: it is only unique among the numbers one
     * country issues. (The phone number already carries its country's calling code.)
     */
    @org.springframework.data.jpa.repository.Query("""
            select count(a) > 0 from TechnicianApplication a
            where a.emailNormalized = :email
               or a.phoneNormalized = :phone
               or (a.idNumber = :idNumber and a.address.country = :country)
            """)
    boolean existsByAnyIdentifier(
            @org.springframework.data.repository.query.Param("email") String email,
            @org.springframework.data.repository.query.Param("phone") String phone,
            @org.springframework.data.repository.query.Param("idNumber") String idNumber,
            @org.springframework.data.repository.query.Param("country") silver.solutions.techconnect.entity.Country country);

    long countByStatus(ApplicationStatus status);

    /** The application an account was created from, if it was created from one. */
    Optional<TechnicianApplication> findByUserId(UUID userId);

    /**
     * Row-locked read for approve/reject, so two Managers deciding the same application at the
     * same moment serialise instead of both succeeding (which would provision two accounts or
     * overwrite a rejection).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from TechnicianApplication a where a.id = :id")
    Optional<TechnicianApplication> findByIdForUpdate(@Param("id") UUID id);
}
