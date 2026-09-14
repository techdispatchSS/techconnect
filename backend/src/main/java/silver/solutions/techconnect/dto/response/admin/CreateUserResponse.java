package silver.solutions.techconnect.dto.response.admin;

/**
 * The activation URL is returned to the Manager, not just emailed, so onboarding works before
 * any mail provider is configured and when a technician's inbox is unreachable. It is
 * single-use and expires in 72 hours.
 */
public record CreateUserResponse(UserResponse user, String activationUrl) {}
