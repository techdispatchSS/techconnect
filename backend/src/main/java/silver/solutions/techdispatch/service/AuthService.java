package silver.solutions.techdispatch.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import silver.solutions.techdispatch.dto.request.auth.UpdateProfileRequest;
import silver.solutions.techdispatch.dto.response.auth.LoginResponse;
import silver.solutions.techdispatch.dto.response.auth.ProfileResponse;
import silver.solutions.techdispatch.entity.AdminAuditAction;
import silver.solutions.techdispatch.entity.TokenPurpose;
import silver.solutions.techdispatch.entity.User;
import silver.solutions.techdispatch.entity.UserRole;
import silver.solutions.techdispatch.entity.UserStatus;
import silver.solutions.techdispatch.exception.ApiException;
import silver.solutions.techdispatch.mapper.AddressMapper;
import silver.solutions.techdispatch.mapper.UserMapper;
import silver.solutions.techdispatch.repository.UserRepository;
import silver.solutions.techdispatch.util.ChangeTracker;
import silver.solutions.techdispatch.util.Strings;

/** Login, activation, and password lifecycle (PRD FR-01, §8.1, §9.2). */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** §8.1 specifies this exact message for a failed login. */
    private static final String INVALID_CREDENTIALS = "Invalid credentials";

    /** Shown only after the correct password was supplied — see {@link #login}. */
    private static final String ACCOUNT_DEACTIVATED =
            "Your account has been deactivated. Contact your manager to restore access.";

    private static final String ACCOUNT_LOCKED =
            "Too many failed sign-in attempts. Try again shortly, or reset your password.";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ActivationTokenService activationTokens;
    private final NotificationService notifications;
    private final LoginAttemptService loginAttempts;
    private final AdminAuditService audit;
    private final UserMapper userMapper;
    private final AddressMapper addressMapper;

    public AuthService(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            ActivationTokenService activationTokens,
            NotificationService notifications,
            LoginAttemptService loginAttempts,
            AdminAuditService audit,
            UserMapper userMapper,
            AddressMapper addressMapper) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.activationTokens = activationTokens;
        this.notifications = notifications;
        this.loginAttempts = loginAttempts;
        this.audit = audit;
        this.userMapper = userMapper;
        this.addressMapper = addressMapper;
    }

    /**
     * Authenticates, and explains itself only to someone who proved they own the account.
     *
     * <p>The password is verified <em>before</em> account status is considered, and the
     * generic "Invalid credentials" of §8.1 is used for every case where it was not correct:
     * unknown address, wrong password, or an account that has never been activated. Only once
     * the password matches will this say why sign-in was refused.
     *
     * <p>That ordering is the whole point. Announcing "this account is deactivated" to any
     * caller would turn the login endpoint into a directory — probe addresses, learn who works
     * here and who used to. Requiring the correct password first means a genuine offboarded
     * technician gets a straight answer while an attacker learns nothing they did not already
     * know. This is a deliberate, narrow divergence from §8.1, which specifies the generic
     * message for all 401s.
     */
    @Transactional
    public LoginResponse login(String email, String rawPassword, String sourceIp) {
        Optional<User> found = users.findByEmailIgnoreCase(email);

        if (found.isEmpty()) {
            log.warn("Failed login for unknown email {} from {}", email, sourceIp);
            throw unauthorized(INVALID_CREDENTIALS);
        }

        User user = found.get();

        // PENDING_ACTIVATION has no hash, so there is no password to prove ownership with.
        // Staying generic here keeps invited-but-inactive accounts undiscoverable.
        if (user.getPasswordHash() == null) {
            log.warn("Login attempt on {} account {} from {}", user.getStatus(), email, sourceIp);
            throw unauthorized(INVALID_CREDENTIALS);
        }

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            // Committed in its own transaction — see LoginAttemptService — because the
            // throw below rolls this one back.
            loginAttempts.recordFailure(user.getId(), email, sourceIp);
            throw unauthorized(INVALID_CREDENTIALS);
        }

        // Password confirmed from here on, so the caller owns this account and may be told why.
        if (user.isLocked()) {
            log.warn("Correct password on locked account {} from {}", email, sourceIp);
            throw unauthorized(ACCOUNT_LOCKED);
        }

        if (user.getStatus() == UserStatus.DISABLED) {
            log.warn("Correct password on deactivated account {} from {}", email, sourceIp);
            throw unauthorized(ACCOUNT_DEACTIVATED);
        }

        if (!user.isActive()) {
            log.warn("Login attempt on {} account {} from {}", user.getStatus(), email, sourceIp);
            throw unauthorized(INVALID_CREDENTIALS);
        }

        clearLockState(user);
        users.save(user);

        JwtService.IssuedToken token = jwtService.issue(user);
        return new LoginResponse(
                token.value(),
                user.getRole().name(),
                user.getId().toString(),
                user.getName());
    }

    @Transactional(readOnly = true)
    public ProfileResponse profile(UUID userId) {
        return userMapper.toProfileResponse(require(userId));
    }

    /**
     * Self-service edit of the signed-in user's own record.
     *
     * <p>Address is applied only for a MANAGER. For a technician the address is the origin
     * point for distance-based job matching, so letting them edit it would let them quietly
     * change which jobs they get offered — that is a dispatch decision, and it stays with the
     * Manager. A non-manager's submitted address is ignored rather than rejected, so a stale
     * client cannot fail a legitimate name change.
     *
     * <p>Recorded with actor and target both set to {@code userId} — the audit trail (FR-09)
     * distinguishes a self-edit from a Manager editing someone else via the action itself
     * ({@link AdminAuditAction#SELF_PROFILE_UPDATED} vs {@link AdminAuditAction#USER_UPDATED}),
     * not by actor-equals-target alone, so a filter on either action reads unambiguously.
     */
    @Transactional
    public ProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = require(userId);
        ChangeTracker tracker = new ChangeTracker();

        tracker.apply("name", user.getName(), request.name().trim(), user::setName);
        tracker.apply("phone", user.getPhone(), Strings.blankToNull(request.phone()), user::setPhone);

        if (user.getRole() == UserRole.MANAGER && request.address() != null) {
            tracker.apply("address", user.getAddress(), addressMapper.toEntity(request.address()),
                    user::setAddress);
        }

        users.save(user);

        if (!tracker.isEmpty()) {
            audit.record(userId, AdminAuditAction.SELF_PROFILE_UPDATED, userId, tracker.changes());
        }

        return userMapper.toProfileResponse(user);
    }

    /** Consumes an activation link and sets the user's first password. */
    @Transactional
    public void activate(String token, String rawPassword) {
        UUID userId = activationTokens.consume(token, TokenPurpose.ACTIVATION);
        User user = users.findById(userId)
                .orElseThrow(() -> ApiException.badRequest("This link is no longer valid."));

        if (user.getStatus() == UserStatus.DISABLED) {
            throw ApiException.badRequest("This account has been deactivated.");
        }

        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setStatus(UserStatus.ACTIVE);
        user.setActivatedAt(Instant.now());
        clearLockState(user);
        users.save(user);

        log.info("Account activated for {}", user.getEmail());
    }

    /**
     * Always completes without error, whether or not the address belongs to an account.
     * A 404 here would turn this endpoint into a way to enumerate every real user.
     */
    @Transactional
    public void forgotPassword(String email) {
        users.findByEmailIgnoreCase(email)
                .filter(user -> user.getStatus() != UserStatus.DISABLED)
                .ifPresentOrElse(
                        user -> {
                            String token = activationTokens.issue(
                                    user.getId(), TokenPurpose.PASSWORD_RESET);
                            notifications.sendPasswordReset(
                                    user, activationTokens.passwordResetUrl(token));
                        },
                        () -> log.info("Password reset requested for unknown or disabled "
                                + "address {} — no email sent", email));
    }

    @Transactional
    public void resetPassword(String token, String rawPassword) {
        UUID userId = activationTokens.consume(token, TokenPurpose.PASSWORD_RESET);
        User user = users.findById(userId)
                .orElseThrow(() -> ApiException.badRequest("This link is no longer valid."));

        if (user.getStatus() == UserStatus.DISABLED) {
            throw ApiException.badRequest("This account has been deactivated.");
        }

        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        // A reset is the remedy for a suspected compromise, so every session opened with
        // the old password has to stop working immediately.
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setStatus(UserStatus.ACTIVE);
        clearLockState(user);
        users.save(user);

        log.info("Password reset completed for {}", user.getEmail());
    }

    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setTokenVersion(user.getTokenVersion() + 1);
        users.save(user);

        log.info("Password changed for {}", user.getEmail());
    }

    private User require(UUID userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
    }

    private void clearLockState(User user) {
        loginAttempts.clear(user);
    }

    private static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, message);
    }
}
