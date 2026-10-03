package silver.solutions.techconnect.dto.request.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A reason is mandatory: a rejection is permanent, so the record must say why. */
public record RejectApplicationRequest(@NotBlank @Size(max = 1000) String reason) {}
