package silver.solutions.techconnect.dto.request.technician;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Where the technician's phone says they are (FR-05), sent when the app opens or comes back. */
public record ReportLocationRequest(
        @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude) {
}
