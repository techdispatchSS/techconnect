package silver.solutions.techconnect.controller;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import silver.solutions.techconnect.dto.request.application.SubmitApplicationRequest;
import silver.solutions.techconnect.dto.response.application.ApplicationReceivedResponse;
import silver.solutions.techconnect.service.ApplicationService;

/**
 * The public technician-application endpoint: the only unauthenticated route that accepts file
 * uploads, and deliberately separate from {@code /v1/auth}. It creates no account and returns no
 * token. Abuse limits (per-address rate limit, request-size ceiling) are enforced before this
 * method runs, by {@code ApplicationSubmissionFilter}.
 */
@RestController
@RequestMapping("/v1/applications")
public class ApplicationController {

    private final ApplicationService applications;

    public ApplicationController(ApplicationService applications) {
        this.applications = applications;
    }

    /** Always 202 with the same body, whether the application was stored or quietly discarded. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApplicationReceivedResponse> submit(
            @Valid @RequestPart("application") SubmitApplicationRequest application,
            @RequestPart("files") List<MultipartFile> files) {

        applications.submit(application, files);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApplicationReceivedResponse.standard());
    }
}
