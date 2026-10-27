package silver.solutions.techconnect.dto.response.technician;

import java.util.List;
import silver.solutions.techconnect.dto.response.controller.JobDetailResponse;
import silver.solutions.techconnect.entity.JobStatus;

/**
 * A job as its technician sees it: the same record the controller follows, plus the steps
 * they can take from here, so the app offers exactly what the server will accept.
 */
public record MyJobResponse(JobDetailResponse detail, List<JobStatus> nextSteps) {
}
