package silver.solutions.techconnect.dto.request.technician;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A device turning job alerts off. In a body, as a token doesn't belong in a logged URL. */
public record UnregisterPushDeviceRequest(@NotBlank @Size(max = 4096) String token) {
}
