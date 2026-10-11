package silver.solutions.techconnect.dto.response.admin;

/**
 * Approving provisions the technician's account, so the single-use activation link is returned
 * to the Manager as well as emailed (same reasoning as {@link CreateUserResponse}).
 */
public record ApproveApplicationResponse(ApplicationDetailResponse application, String activationUrl) {}
