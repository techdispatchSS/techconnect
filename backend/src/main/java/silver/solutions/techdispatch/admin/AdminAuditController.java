package silver.solutions.techdispatch.admin;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import silver.solutions.techdispatch.admin.dto.AdminDtos.AuditEntryResponse;
import silver.solutions.techdispatch.common.PageResponse;
import silver.solutions.techdispatch.domain.AdminAuditAction;

/**
 * Read-only view of the administrative audit trail (FR-09). There is deliberately no write,
 * edit or delete endpoint: audit records "shall not be editable or deletable by any user".
 */
@RestController
@RequestMapping("/v1/admin/audit")
public class AdminAuditController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminAuditService audit;

    public AdminAuditController(AdminAuditService audit) {
        this.audit = audit;
    }

    /**
     * @param q free text matched against actor and target names/emails and the detail payload
     * @param from inclusive start date (ISO, {@code yyyy-MM-dd}), interpreted in UTC
     * @param to inclusive end date; widened to the end of that day so a single-day range works
     */
    @GetMapping
    public PageResponse<AuditEntryResponse> list(
            @RequestParam(required = false) UUID targetUserId,
            @RequestParam(required = false) AdminAuditAction action,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var pageable = PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        return audit.list(
                targetUserId,
                action,
                q,
                from == null ? null : from.atStartOfDay(ZoneOffset.UTC).toInstant(),
                // Exclusive upper bound on the day *after* `to`, so entries recorded during
                // the end date itself are included rather than silently dropped.
                to == null ? null : to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
                pageable);
    }
}
