package silver.solutions.techconnect.dto.request.technician;

import jakarta.validation.constraints.NotNull;

/**
 * A technician going on or off duty (T-14). Only the two states a person can choose:
 * {@code ON_JOB} is the system's to set, on accepting a dispatch.
 */
public record UpdateAvailabilityRequest(@NotNull Boolean available) {
}
