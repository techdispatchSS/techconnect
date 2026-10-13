package silver.solutions.techconnect.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import silver.solutions.techconnect.config.TechConnectProperties;
import silver.solutions.techconnect.dto.request.application.SubmitApplicationRequest;
import silver.solutions.techconnect.dto.request.application.SubmitApplicationRequest.DocumentMeta;
import silver.solutions.techconnect.entity.ApplicationDocument;
import silver.solutions.techconnect.entity.DocumentKind;
import silver.solutions.techconnect.entity.TechnicianApplication;
import silver.solutions.techconnect.exception.ApiException;
import silver.solutions.techconnect.mapper.AddressMapper;
import silver.solutions.techconnect.repository.ApplicationDocumentRepository;
import silver.solutions.techconnect.repository.TechnicianApplicationRepository;
import silver.solutions.techconnect.repository.UserRepository;
import silver.solutions.techconnect.security.UploadInspector;
import silver.solutions.techconnect.security.UploadInspector.Inspected;
import silver.solutions.techconnect.util.ApplicantIdentity;
import silver.solutions.techconnect.util.Strings;

/**
 * Accepts a public technician application (no account, no login) together with its proof of
 * certificates and qualifications.
 *
 * <p>Two behaviours here are deliberate and security-relevant:
 *
 * <ul>
 *   <li><b>One application per person, forever.</b> A repeat submission, or one from anyone whose
 *       email or phone already belongs to an application (including a rejected one) or an
 *       account, is discarded. This is what makes a rejection permanent.
 *   <li><b>The caller can't tell.</b> A discarded submission gets the same response as an
 *       accepted one, and no email is sent for it, so this endpoint can't be used to find out who
 *       has applied or been rejected, and can't be used to spam someone's inbox by submitting
 *       their address over and over.
 * </ul>
 */
@Service
public class ApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);

    private final TechnicianApplicationRepository applications;
    private final ApplicationDocumentRepository documents;
    private final UserRepository users;
    private final DocumentStorage storage;
    private final UploadInspector inspector;
    private final NotificationService notifications;
    private final TechConnectProperties properties;
    private final AddressMapper addressMapper;
    private final TransactionTemplate transaction;

    public ApplicationService(
            TechnicianApplicationRepository applications,
            ApplicationDocumentRepository documents,
            UserRepository users,
            DocumentStorage storage,
            UploadInspector inspector,
            NotificationService notifications,
            TechConnectProperties properties,
            AddressMapper addressMapper,
            TransactionTemplate transaction) {
        this.applications = applications;
        this.documents = documents;
        this.users = users;
        this.storage = storage;
        this.inspector = inspector;
        this.notifications = notifications;
        this.properties = properties;
        this.addressMapper = addressMapper;
        this.transaction = transaction;
    }

    /** One file that has passed inspection and is ready to store. */
    private record Prepared(String key, DocumentMeta meta, Inspected info, byte[] bytes) {}

    public void submit(SubmitApplicationRequest request, List<MultipartFile> files) {
        // A filled-in honeypot means a bot. Answer exactly like a success so it learns nothing.
        if (request.website() != null && !request.website().isBlank()) {
            log.warn("Application discarded: honeypot field was filled in");
            pause();
            return;
        }

        List<Prepared> prepared = inspectAll(request, files);

        String email = request.email().trim();
        String emailKey = ApplicantIdentity.normalizeEmail(email);
        String phoneKey = ApplicantIdentity.normalizePhone(request.phone());

        if (phoneKey.length() < 9) {
            throw ApiException.badRequest("Enter a valid phone number.");
        }

        // Checked before anything is uploaded, so repeat/blocked submissions cost no storage.
        if (isKnown(email, emailKey, phoneKey, request.idNumber())) {
            log.info("Application discarded: applicant already known");
            pause();
            return;
        }

        List<String> uploaded = new ArrayList<>();
        try {
            for (Prepared file : prepared) {
                storage.put(file.key(), file.bytes(), file.info().contentType());
                uploaded.add(file.key());
            }
            transaction.executeWithoutResult(status ->
                    save(request, email, emailKey, phoneKey, prepared));
        } catch (DataIntegrityViolationException e) {
            cleanUp(uploaded);
            // The unique indexes are the real guard: two simultaneous submissions both pass the
            // check above, and the database lets exactly one through. Anything else that trips a
            // constraint is a genuine bug and must not be silently swallowed.
            if (isKnown(email, emailKey, phoneKey, request.idNumber())) {
                log.info("Application discarded: lost a race with an identical submission");
                pause();
                return;
            }
            throw e;
        } catch (RuntimeException e) {
            cleanUp(uploaded);
            throw e;
        }

        log.info("Technician application received with {} document(s)", prepared.size());
        notifications.sendApplicationReceived(email, Strings.cleanText(request.firstName()));
    }

    // --- validation ------------------------------------------------------------------------

    private List<Prepared> inspectAll(SubmitApplicationRequest request, List<MultipartFile> files) {
        TechConnectProperties.Applications limits = properties.applications();

        if (files == null || files.isEmpty()) {
            throw ApiException.badRequest(missingDocumentsMessage(DocumentKind.REQUIRED));
        }
        if (files.size() > limits.maxFiles()) {
            throw ApiException.badRequest("You can attach at most " + limits.maxFiles() + " files.");
        }
        if (files.size() != request.documents().size()) {
            throw ApiException.badRequest("Each file needs a type and a title.");
        }
        requireIdentityDocuments(request.documents());

        long total = files.stream().mapToLong(MultipartFile::getSize).sum();
        if (total > limits.maxRequestSize().toBytes()) {
            throw ApiException.badRequest("Those files are too large in total.");
        }

        List<Prepared> prepared = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            byte[] bytes;
            try {
                bytes = file.getBytes();
            } catch (IOException e) {
                throw ApiException.badRequest("One of your files could not be read.");
            }

            Inspected info = inspector.inspect(
                    file.getOriginalFilename(),
                    file.getContentType(),
                    bytes,
                    limits.maxFileSize().toBytes());

            // Random, unguessable, and says nothing about the applicant or the filename.
            String key = "applications/" + UUID.randomUUID();
            prepared.add(new Prepared(key, request.documents().get(i), info, bytes));
        }
        return prepared;
    }

    /**
     * Every application carries exactly one ID document, one matric certificate and one proof of
     * residence. Certifications are optional and there can be several.
     */
    private void requireIdentityDocuments(List<DocumentMeta> metas) {
        List<DocumentKind> missing = new ArrayList<>();
        for (DocumentKind kind : DocumentKind.REQUIRED) {
            long count = metas.stream().filter(meta -> meta.kind() == kind).count();
            if (count == 0) {
                missing.add(kind);
            } else if (count > 1) {
                throw ApiException.badRequest("Attach only one " + kind.label() + ".");
            }
        }
        if (!missing.isEmpty()) {
            throw ApiException.badRequest(missingDocumentsMessage(missing));
        }
    }

    private static String missingDocumentsMessage(List<DocumentKind> missing) {
        return "Attach your "
                + String.join(", ", missing.stream().map(DocumentKind::label).toList())
                + ".";
    }

    private boolean isKnown(String email, String emailKey, String phoneKey, String idNumber) {
        return applications.existsByEmailNormalizedOrPhoneNormalizedOrIdNumber(emailKey, phoneKey, idNumber)
                || users.existsByEmailIgnoreCase(email);
    }

    // --- persistence -----------------------------------------------------------------------

    private void save(
            SubmitApplicationRequest request,
            String email,
            String emailKey,
            String phoneKey,
            List<Prepared> prepared) {

        TechnicianApplication application = new TechnicianApplication();
        application.setFirstName(Strings.cleanText(request.firstName()));
        application.setLastName(Strings.cleanText(request.lastName()));
        application.setEmail(email);
        application.setEmailNormalized(emailKey);
        application.setPhone(request.phone().trim());
        application.setPhoneNormalized(phoneKey);
        application.setIdNumber(request.idNumber());
        application.setAddress(addressMapper.toEntity(request.address()));
        application.setExperienceSummary(Strings.cleanText(request.experienceSummary()));
        application.setSkills(Strings.blankToNull(Strings.cleanText(request.skills())));
        applications.saveAndFlush(application);

        for (Prepared file : prepared) {
            ApplicationDocument document = new ApplicationDocument();
            document.setApplicationId(application.getId());
            document.setKind(file.meta().kind());
            document.setTitle(Strings.cleanText(file.meta().title()));
            document.setOriginalFilename(file.info().safeFilename());
            document.setContentType(file.info().contentType());
            document.setSizeBytes(file.info().sizeBytes());
            document.setSha256(file.info().sha256());
            document.setStorageKey(file.key());
            documents.save(document);
        }
    }

    private void cleanUp(List<String> keys) {
        keys.forEach(storage::delete);
    }

    /**
     * A short random delay on the discarded paths, so a quick "nothing happened" answer can't be
     * told apart from the slower path that uploads and stores files.
     */
    private void pause() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(250, 750));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
