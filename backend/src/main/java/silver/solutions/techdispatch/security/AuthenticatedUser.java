package silver.solutions.techdispatch.security;

import java.util.UUID;
import silver.solutions.techdispatch.entity.UserRole;

/**
 * The authenticated principal exposed to controllers. Resolved from the JWT on every
 * request and re-checked against the database, so it always reflects live account state
 * rather than whatever was true when the token was minted.
 */
public record AuthenticatedUser(UUID id, String email, String name, UserRole role) {
}
