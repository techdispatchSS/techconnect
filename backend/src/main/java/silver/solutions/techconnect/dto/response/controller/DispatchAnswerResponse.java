package silver.solutions.techconnect.dto.response.controller;

import java.util.UUID;

/** The result of a technician accepting or declining a dispatch. */
public record DispatchAnswerResponse(UUID dispatchId, String outcome) {
}
