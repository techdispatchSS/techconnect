package silver.solutions.techconnect.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The IANA zone id the caller's browser reports, e.g. {@code Africa/Nairobi}. */
public record UpdateTimeZoneRequest(@NotBlank @Size(max = 64) String timeZone) {}
