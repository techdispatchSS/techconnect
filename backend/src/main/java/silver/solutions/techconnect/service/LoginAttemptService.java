package silver.solutions.techconnect.service;

import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import silver.solutions.techconnect.config.TechConnectProperties;
import silver.solutions.techconnect.entity.User;
import silver.solutions.techconnect.repository.UserRepository;

/**
 * Tracks consecutive failed logins and locks an account once they cross the threshold (§9.2).
 *
 * <p>This lives in its own bean purely so the counter can be written in a
 * {@link Propagation#REQUIRES_NEW} transaction. A failed login ends by throwing, which rolls
 * the caller's transaction back — so an increment written inside that transaction would be
 * discarded by the very exception that signals the failure, and the account would never lock
 * no matter how many attempts were made. Committing separately is what makes the count stick.
 *
 * <p>It must be a separate bean rather than a method on {@code AuthService}: Spring's
 * transactional proxying does not apply to self-invocation, so an internal call would silently
 * inherit the caller's transaction and reintroduce the same bug.
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private final UserRepository users;
    private final TechConnectProperties properties;

    public LoginAttemptService(UserRepository users, TechConnectProperties properties) {
        this.users = users;
        this.properties = properties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID userId, String email, String sourceIp) {
        users.findById(userId).ifPresent(user -> {
            int attempts = user.getFailedLoginAttempts() + 1;
            int max = properties.login().maxFailedAttempts();
            user.setFailedLoginAttempts(attempts);

            if (attempts >= max) {
                user.setLockedUntil(Instant.now().plus(properties.login().lockDuration()));
                log.warn("Account {} locked for {} after {} failed attempts (latest from {})",
                        email, properties.login().lockDuration(), attempts, sourceIp);
            } else {
                log.warn("Failed login {} of {} for {} from {}", attempts, max, email, sourceIp);
            }

            users.save(user);
        });
    }

    /** Clears the counter after a successful authentication. */
    public void clear(User user) {
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
    }
}
