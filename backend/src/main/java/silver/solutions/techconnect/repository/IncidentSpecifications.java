package silver.solutions.techconnect.repository;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import silver.solutions.techconnect.entity.Dispatch;
import silver.solutions.techconnect.entity.DispatchStatus;
import silver.solutions.techconnect.entity.Incident;
import silver.solutions.techconnect.entity.IncidentPriority;
import silver.solutions.techconnect.entity.IncidentStatus;

/** Query predicates backing the controller dashboard's incident queue. */
public final class IncidentSpecifications {

    private IncidentSpecifications() {}

    /** Each argument is optional; a null (or false) one contributes no predicate at all. */
    public static Specification<Incident> matching(
            IncidentStatus status, IncidentPriority priority, String query, boolean unassignedOnly) {

        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (priority != null) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }
            if (query != null && !query.isBlank()) {
                String like = "%" + query.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("clientName")), like),
                        cb.like(cb.lower(root.get("siteAddress")), like),
                        cb.like(cb.lower(root.get("freshdeskTicketId")), like)));
            }
            if (unassignedOnly) {
                Subquery<UUID> subquery = criteriaQuery.subquery(UUID.class);
                var dispatchRoot = subquery.from(Dispatch.class);
                subquery.select(dispatchRoot.get("incidentId"))
                        .where(cb.equal(dispatchRoot.get("incidentId"), root.get("id")),
                                cb.notEqual(dispatchRoot.get("status"), DispatchStatus.EXPIRED));
                predicates.add(cb.not(cb.exists(subquery)));
            }

            return predicates.isEmpty()
                    ? cb.conjunction()
                    : cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
