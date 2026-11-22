package silver.solutions.techconnect.dto.response.controller;

import java.util.UUID;

/** The result of a technician accepting or declining a dispatch. */
public record DispatchAnswerResponse(UUID dispatchId, String outcome, UUID jobId) {

    /** The job an accept opened, so the technician's app can go straight to it. */
    public DispatchAnswerResponse withJob(UUID jobId) {
        return new DispatchAnswerResponse(dispatchId, outcome, jobId);
    }
}
