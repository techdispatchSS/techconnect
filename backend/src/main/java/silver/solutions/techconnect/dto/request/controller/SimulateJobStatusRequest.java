package silver.solutions.techconnect.dto.request.controller;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import silver.solutions.techconnect.entity.JobStatus;

/** Demo only: moves a job on as the technician who holds it. */
public record SimulateJobStatusRequest(@NotNull JobStatus status, @Size(max = 500) String reason) {
}
