package silver.solutions.techdispatch.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import silver.solutions.techdispatch.entity.TokenPurpose;
import silver.solutions.techdispatch.entity.User;
import silver.solutions.techdispatch.entity.UserRole;
import silver.solutions.techdispatch.entity.UserStatus;
import silver.solutions.techdispatch.mapper.UserMapper;
import silver.solutions.techdispatch.repository.UserRepository;
import silver.solutions.techdispatch.service.ActivationTokenService;
import silver.solutions.techdispatch.service.NotificationService;

/**
 * Resolves the chicken-and-egg problem in M-06: only a Manager can create users, so a fresh
 * database has no way in. On first start this creates the configured Manager in
 * {@link UserStatus#PENDING_ACTIVATION} and prints an activation link.
 *
 * <p>This is deliberately not a Flyway seed migration. A migration would either bake a
 * password hash into version control or need to be edited per environment, and it could not
 * re-issue a link if the first one were lost.
 */
@Component
public class BootstrapAdminRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);

    private final UserRepository users;
    private final UserMapper userMapper;
    private final ActivationTokenService activationTokens;
    private final NotificationService notifications;
    private final TechDispatchProperties properties;

    public BootstrapAdminRunner(
            UserRepository users,
            UserMapper userMapper,
            ActivationTokenService activationTokens,
            NotificationService notifications,
            TechDispatchProperties properties) {
        this.users = users;
        this.userMapper = userMapper;
        this.activationTokens = activationTokens;
        this.notifications = notifications;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = properties.bootstrapAdmin().email();

        if (email == null || email.isBlank()) {
            log.info("No techdispatch.bootstrap-admin.email configured — skipping bootstrap "
                    + "admin creation.");
            return;
        }

        // Idempotency is keyed on "does any Manager exist", not on this email, so restarting
        // the app never mints a second administrator or re-issues a live token.
        if (users.existsByRole(UserRole.MANAGER)) {
            log.debug("A MANAGER already exists — bootstrap admin not required.");
            return;
        }

        if (users.existsByEmailIgnoreCase(email)) {
            log.warn("Bootstrap admin email {} is already taken by a non-manager account. "
                    + "No manager was created.", email);
            return;
        }

        User admin = userMapper.toUser(
                email, properties.bootstrapAdmin().name(), UserRole.MANAGER,
                UserStatus.PENDING_ACTIVATION);
        users.save(admin);

        String token = activationTokens.issue(admin.getId(), TokenPurpose.ACTIVATION);
        String activationUrl = activationTokens.activationUrl(token);
        notifications.sendActivationInvite(admin, activationUrl);

        log.info("""

                ============================================================
                 Bootstrap administrator created: {}
                 Set the password using this single-use link (expires in 72h):

                 {}

                ============================================================""",
                admin.getEmail(), activationUrl);
    }
}
