package silver.solutions.techdispatch.dto.request.auth;

/**
 * Password fields are capped at 72 characters because bcrypt silently ignores input beyond 72
 * bytes — accepting a longer one would mean the tail of the user's password does nothing,
 * which is worse than rejecting it.
 */
public final class PasswordConstraints {

    public static final int PASSWORD_MIN = 12;
    public static final int PASSWORD_MAX = 72;

    private PasswordConstraints() {}
}
