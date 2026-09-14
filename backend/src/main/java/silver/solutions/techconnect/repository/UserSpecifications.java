package silver.solutions.techconnect.repository;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import silver.solutions.techconnect.entity.User;
import silver.solutions.techconnect.entity.UserRole;
import silver.solutions.techconnect.entity.UserStatus;

/** Query predicates backing the admin portal's user search. */
public final class UserSpecifications {

    private UserSpecifications() {}

    /**
     * Builds the filter for the admin user list. Each argument is optional; a null one
     * contributes no predicate at all rather than a null bind parameter.
     *
     * @param excludeUserId the signed-in Manager, kept out of the roster they administer —
     *     they manage their own details through their profile menu instead, and offering
     *     themselves a row whose actions are all disabled is just noise
     */
    public static Specification<User> matching(
            UserRole role, UserStatus status, String query, UUID excludeUserId) {

        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (excludeUserId != null) {
                predicates.add(cb.notEqual(root.get("id"), excludeUserId));
            }
            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (query != null && !query.isBlank()) {
                String like = "%" + query.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("email")), like)));
            }

            return predicates.isEmpty()
                    ? cb.conjunction()
                    : cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
