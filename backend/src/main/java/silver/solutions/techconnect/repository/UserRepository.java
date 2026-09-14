package silver.solutions.techconnect.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import silver.solutions.techconnect.entity.User;
import silver.solutions.techconnect.entity.UserRole;
import silver.solutions.techconnect.entity.UserStatus;

/**
 * Optional filtering for the admin user list is expressed with {@link JpaSpecificationExecutor}
 * rather than a JPQL query full of {@code :param IS NULL OR ...} clauses. Postgres cannot infer
 * the type of an unused null bind parameter, so the JPQL form fails at runtime with
 * "function lower(bytea) does not exist" the moment a filter is omitted. A specification simply
 * omits the predicate instead of binding a null.
 */
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    /** Email is the login identity and is matched case-insensitively, matching the unique index. */
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByRole(UserRole role);

    /** The controller dashboard's dispatch-candidate pool (FR-04). */
    List<User> findByRoleAndStatus(UserRole role, UserStatus status);
}
