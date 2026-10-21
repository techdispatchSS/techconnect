package silver.solutions.techconnect.dto.request.controller;

import jakarta.validation.constraints.Size;

/** The controller's sign-off on a submitted job. The notes are optional. */
public record CloseJobRequest(@Size(max = 2000) String closingNotes) {
}
