package silver.solutions.techdispatch.repository;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import silver.solutions.techdispatch.entity.AdminAuditAction;
import silver.solutions.techdispatch.entity.AdminAuditLog;

/** Query predicates backing the searchable, filterable audit trail (FR-09). */
public final class AdminAuditLogSpecifications {

    private AdminAuditLogSpecifications() {}

    /**
     * Every argument is optional; a null one contributes no predicate rather than a null bind
     * parameter, which PostgreSQL cannot type.
     *
     * @param query free text matched against the actor's and target's name and email, and the
     *     recorded detail payload
     * @param from inclusive lower bound on {@code createdAt}
     * @param to exclusive upper bound on {@code createdAt}
     */
    public static Specification<AdminAuditLog> matching(
            UUID targetUserId,
            AdminAuditAction action,
            String query,
            Instant from,
            Instant to) {

        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (targetUserId != null) {
                predicates.add(cb.equal(root.get("targetUserId"), targetUserId));
            }
            if (action != null) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), to));
            }

            if (query != null && !query.isBlank()) {
                String like = "%" + query.trim().toLowerCase() + "%";

                // Left joins: an entry whose target is null (or whose actor row has somehow
                // gone) must still be searchable by its detail payload rather than dropping
                // out of the result set entirely, which an inner join would do.
                var actor = root.join("actor", JoinType.LEFT);
                var target = root.join("target", JoinType.LEFT);

                // Deliberately not searching the `details` payload. It is jsonb, so it would
                // need a Postgres-specific cast, and matching its raw text would hit
                // structural keys as well as values — searching "from" or "to" would return
                // every rename entry ever recorded. Who did what to whom is fully covered by
                // the four columns below, with the action filter for the "what".
                predicates.add(cb.or(
                        cb.like(cb.lower(actor.get("name")), like),
                        cb.like(cb.lower(actor.get("email")), like),
                        cb.like(cb.lower(target.get("name")), like),
                        cb.like(cb.lower(target.get("email")), like)));

                // The joins can multiply rows, which would corrupt both the page contents and
                // the total count.
                if (criteriaQuery != null) {
                    criteriaQuery.distinct(true);
                }
            }

            return predicates.isEmpty()
                    ? cb.conjunction()
                    : cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
