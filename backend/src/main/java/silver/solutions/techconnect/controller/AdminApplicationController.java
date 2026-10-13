package silver.solutions.techconnect.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import silver.solutions.techconnect.dto.request.admin.RejectApplicationRequest;
import silver.solutions.techconnect.dto.response.admin.ApplicationDetailResponse;
import silver.solutions.techconnect.dto.response.admin.ApplicationSummaryResponse;
import silver.solutions.techconnect.dto.response.admin.ApproveApplicationResponse;
import silver.solutions.techconnect.dto.response.admin.PendingApplicationsResponse;
import silver.solutions.techconnect.dto.response.common.PageResponse;
import silver.solutions.techconnect.entity.ApplicationStatus;
import silver.solutions.techconnect.security.AuthenticatedUser;
import silver.solutions.techconnect.service.AdminApplicationService;
import silver.solutions.techconnect.service.AdminApplicationService.DocumentContent;

/**
 * Technician applications, from the Manager's side. MANAGER-only, enforced centrally by the
 * {@code /v1/admin/**} rule in {@code SecurityConfig}: only a Manager can accept or reject.
 */
@RestController
@RequestMapping("/v1/admin/applications")
public class AdminApplicationController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminApplicationService applications;

    public AdminApplicationController(AdminApplicationService applications) {
        this.applications = applications;
    }

    @GetMapping
    public PageResponse<ApplicationSummaryResponse> list(
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var pageable = PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        return applications.list(status, q, pageable);
    }

    /** For the sidebar badge. */
    @GetMapping("/pending-count")
    public PendingApplicationsResponse pendingCount() {
        return new PendingApplicationsResponse(applications.pendingCount());
    }

    @GetMapping("/{id}")
    public ApplicationDetailResponse get(@PathVariable UUID id) {
        return applications.get(id);
    }

    /** The application behind a technician's account, for their record in People. */
    @GetMapping("/by-user/{userId}")
    public ApplicationDetailResponse forUser(@PathVariable UUID userId) {
        return applications.getForUser(userId);
    }

    /**
     * Serves an applicant's uploaded document. These are untrusted files from an anonymous
     * source, so they are never rendered in the page: always a download ({@code attachment}), the
     * type is not sniffable, and a CSP of {@code sandbox} with no sources means that even if a
     * browser were persuaded to display one inline, nothing in it could run or load anything.
     */
    @GetMapping("/{id}/documents/{documentId}")
    public ResponseEntity<byte[]> document(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @PathVariable UUID documentId) {

        DocumentContent content = applications.document(principal.id(), id, documentId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .contentLength(content.bytes().length)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(content.filename()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .cacheControl(CacheControl.noStore())
                .body(content.bytes());
    }

    @PostMapping("/{id}/approve")
    public ApproveApplicationResponse approve(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        return applications.approve(principal.id(), id);
    }

    @PostMapping("/{id}/reject")
    public ApplicationDetailResponse reject(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @Valid @RequestBody RejectApplicationRequest request) {
        return applications.reject(principal.id(), id, request.reason());
    }
}
