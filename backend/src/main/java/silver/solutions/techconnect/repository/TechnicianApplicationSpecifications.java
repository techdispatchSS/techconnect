package silver.solutions.techconnect.repository;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import silver.solutions.techconnect.entity.ApplicationStatus;
import silver.solutions.techconnect.entity.TechnicianApplication;

/** Query predicates backing the Manager's applications list. */
public final class TechnicianApplicationSpecifications {

    private TechnicianApplicationSpecifications() {}

    public static Specification<TechnicianApplication> matching(
            ApplicationStatus status, String query) {

        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (query != null && !query.isBlank()) {
                // Bound as a parameter, never concatenated into the query: the search box is
                // only reachable by a Manager, but it is still untrusted text.
                String like = "%" + escapeLike(query.trim().toLowerCase()) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("firstName")), like, '\\'),
                        cb.like(cb.lower(root.get("lastName")), like, '\\'),
                        cb.like(cb.lower(root.get("email")), like, '\\')));
            }

            return predicates.isEmpty()
                    ? cb.conjunction()
                    : cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** Stops a typed {@code %} or {@code _} acting as a wildcard (e.g. {@code %} matching all). */
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
