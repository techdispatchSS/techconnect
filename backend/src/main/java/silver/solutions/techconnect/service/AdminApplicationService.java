package silver.solutions.techconnect.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import silver.solutions.techconnect.dto.response.admin.ApplicationDetailResponse;
import silver.solutions.techconnect.dto.response.admin.ApplicationSummaryResponse;
import silver.solutions.techconnect.dto.response.admin.ApproveApplicationResponse;
import silver.solutions.techconnect.dto.response.admin.CreateUserResponse;
import silver.solutions.techconnect.dto.response.common.PageResponse;
import silver.solutions.techconnect.entity.AdminAuditAction;
import silver.solutions.techconnect.entity.ApplicationDocument;
import silver.solutions.techconnect.entity.ApplicationStatus;
import silver.solutions.techconnect.entity.TechnicianApplication;
import silver.solutions.techconnect.exception.ApiException;
import silver.solutions.techconnect.mapper.ApplicationMapper;
import silver.solutions.techconnect.repository.ApplicationDocumentRepository;
import silver.solutions.techconnect.repository.TechnicianApplicationRepository;
import silver.solutions.techconnect.repository.TechnicianApplicationSpecifications;
import silver.solutions.techconnect.repository.UserRepository;
import silver.solutions.techconnect.util.Strings;

/**
 * The Manager's side of technician applications: review, open the uploaded proof, accept or
 * reject. Reachable only through {@code /v1/admin/**}, which {@code SecurityConfig} restricts to
 * the MANAGER role, so no Controller or Technician can decide anyone's application.
 */
@Service
public class AdminApplicationService {

    private static final Logger log = LoggerFactory.getLogger(AdminApplicationService.class);

    private final TechnicianApplicationRepository applications;
    private final ApplicationDocumentRepository documents;
    private final UserRepository users;
    private final AdminUserService adminUsers;
    private final AdminAuditService audit;
    private final NotificationService notifications;
    private final DocumentStorage storage;
    private final ApplicationMapper mapper;

    public AdminApplicationService(
            TechnicianApplicationRepository applications,
            ApplicationDocumentRepository documents,
            UserRepository users,
            AdminUserService adminUsers,
            AdminAuditService audit,
            NotificationService notifications,
            DocumentStorage storage,
            ApplicationMapper mapper) {
        this.applications = applications;
        this.documents = documents;
        this.users = users;
        this.adminUsers = adminUsers;
        this.audit = audit;
        this.notifications = notifications;
        this.storage = storage;
        this.mapper = mapper;
    }

    /** A downloaded document: bytes plus the metadata needed to serve them safely. */
    public record DocumentContent(String filename, String contentType, byte[] bytes) {}

    @Transactional(readOnly = true)
    public PageResponse<ApplicationSummaryResponse> list(
            ApplicationStatus status, String query, Pageable pageable) {

        Page<TechnicianApplication> page =
                applications.findAll(TechnicianApplicationSpecifications.matching(status, query), pageable);

        // One query for every document on the page, rather than one per row.
        Map<UUID, Long> counts = documents
                .findByApplicationIdIn(page.getContent().stream().map(TechnicianApplication::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(ApplicationDocument::getApplicationId, Collectors.counting()));

        return PageResponse.of(page, application ->
                mapper.toSummary(application, counts.getOrDefault(application.getId(), 0L).intValue()));
    }

    @Transactional(readOnly = true)
    public ApplicationDetailResponse get(UUID id) {
        return detail(require(id));
    }

    /**
     * The application a technician's account was created from, with its documents. This is how a
     * Manager reaches someone's proof of ID, matric and residence from their record in People
     * once they have been accepted. A person added directly by a Manager has none.
     */
    @Transactional(readOnly = true)
    public ApplicationDetailResponse getForUser(UUID userId) {
        return detail(applications.findByUserId(userId)
                .orElseThrow(() -> ApiException.notFound("No application on file for this person.")));
    }

    @Transactional(readOnly = true)
    public long pendingCount() {
        return applications.countByStatus(ApplicationStatus.PENDING);
    }

    /**
     * Opens one of an applicant's documents. Recorded in the audit trail: these are identity-
     * adjacent personal documents, and who looked at them is exactly what a POPIA question asks.
     */
    @Transactional
    public DocumentContent document(UUID actorId, UUID applicationId, UUID documentId) {
        ApplicationDocument document = documents.findById(documentId)
                // The pair must match: a valid document id under the wrong application is 404,
                // not a way to read some other applicant's file by guessing the URL.
                .filter(d -> d.getApplicationId().equals(applicationId))
                .orElseThrow(() -> ApiException.notFound("Document not found"));

        byte[] bytes = storage.get(document.getStorageKey());

        audit.record(actorId, AdminAuditAction.APPLICATION_DOCUMENT_VIEWED, null, Map.of(
                "applicationId", applicationId.toString(),
                "documentId", documentId.toString(),
                "filename", document.getOriginalFilename()));

        return new DocumentContent(document.getOriginalFilename(), document.getContentType(), bytes);
    }

    /**
     * Accepts an applicant: provisions their Technician account exactly as "Add user" would
     * (pending activation, technician profile, audit row, emailed invite) and links it back.
     */
    @Transactional
    public ApproveApplicationResponse approve(UUID actorId, UUID id) {
        TechnicianApplication application = requirePendingForUpdate(id);

        CreateUserResponse created = adminUsers.provisionTechnician(
                actorId,
                application.getFirstName(),
                application.getLastName(),
                application.getEmail(),
                application.getPhone(),
                application.getAddress());

        application.setStatus(ApplicationStatus.APPROVED);
        application.setDecidedBy(actorId);
        application.setDecidedAt(java.time.Instant.now());
        application.setUserId(created.user().id());
        applications.save(application);

        audit.record(actorId, AdminAuditAction.APPLICATION_APPROVED, created.user().id(), Map.of(
                "applicationId", application.getId().toString(),
                "email", application.getEmail()));

        log.info("Application {} approved by {}", application.getId(), actorId);
        return new ApproveApplicationResponse(detail(application), created.activationUrl());
    }

    /**
     * Rejects an applicant, permanently. The record stays (that is what stops them applying
     * again), a reason is mandatory, and the applicant is told by email.
     */
    @Transactional
    public ApplicationDetailResponse reject(UUID actorId, UUID id, String reason) {
        TechnicianApplication application = requirePendingForUpdate(id);
        String cleaned = Strings.cleanText(reason);
        if (cleaned == null || cleaned.isBlank()) {
            throw ApiException.badRequest("Give a reason for rejecting this application.");
        }

        application.setStatus(ApplicationStatus.REJECTED);
        application.setDecidedBy(actorId);
        application.setDecidedAt(java.time.Instant.now());
        application.setDecisionReason(cleaned);
        applications.save(application);

        audit.record(actorId, AdminAuditAction.APPLICATION_REJECTED, null, Map.of(
                "applicationId", application.getId().toString(),
                "email", application.getEmail(),
                "reason", cleaned));

        notifications.sendApplicationRejected(application.getEmail(), application.getFirstName());
        log.info("Application {} rejected by {}", application.getId(), actorId);
        return detail(application);
    }

    // --- helpers ---------------------------------------------------------------------------

    private TechnicianApplication require(UUID id) {
        return applications.findById(id)
                .orElseThrow(() -> ApiException.notFound("Application not found"));
    }

    /** Locks the row so two Managers can't decide the same application at once. */
    private TechnicianApplication requirePendingForUpdate(UUID id) {
        TechnicianApplication application = applications.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("Application not found"));
        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw ApiException.conflict("This application has already been decided.");
        }
        return application;
    }

    private ApplicationDetailResponse detail(TechnicianApplication application) {
        List<ApplicationDocument> docs =
                documents.findByApplicationIdOrderByCreatedAtAsc(application.getId());
        String decidedBy = application.getDecidedBy() == null
                ? null
                : users.findById(application.getDecidedBy()).map(u -> u.getName()).orElse(null);
        return mapper.toDetail(application, docs, decidedBy);
    }
}
