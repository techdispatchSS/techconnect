package silver.solutions.techconnect.dto.request.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * A technician's report on finished work (PRD FR-06). Reaches the service through the
 * simulation endpoint today, and through the technician app once that exists.
 */
public record JobReportRequest(
        @NotBlank @Size(max = 4000) String resolutionNotes,
        @Size(max = 2000) String rootCause,
        @Size(max = 2000) String recommendations,
        @NotNull @DecimalMin("0.0") @DecimalMax("999.99") BigDecimal labourHours,
        @Valid @Size(max = 50) List<Part> parts) {

    public record Part(
            @NotBlank @Size(max = 255) String name,
            @Min(1) @Max(10_000) int quantity,
            @Size(max = 128) String serialNumber) {
    }
}
