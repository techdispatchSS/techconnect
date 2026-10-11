package silver.solutions.techconnect.dto.request.controller;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import silver.solutions.techconnect.entity.IncidentPriority;

/**
 * An incident a Controller logs directly, typically from a phone call. Sizes mirror the column
 * widths in the schema so an over-long value is a clean 400 rather than a database error.
 */
public record CreateIncidentRequest(
        @NotBlank @Size(max = 255) String clientName,
        @Size(max = 500) String siteAddress,
        /** Both or neither; supplied when the address was picked from a map lookup. */
        @DecimalMin("-90") @DecimalMax("90") BigDecimal siteLatitude,
        @DecimalMin("-180") @DecimalMax("180") BigDecimal siteLongitude,
        @Size(max = 255) String contactName,
        @Size(max = 32) @Pattern(regexp = "^[0-9+()\\- ]*$") String contactPhone,
        @Size(max = 128) String issueType,
        @NotNull IncidentPriority priority,
        /** Response target in hours; defaults from the priority when omitted. */
        @Min(1) @Max(168) Integer slaHours,
        @Size(max = 4000) String description,
        @Size(max = 4000) String additionalNotes) {
}
