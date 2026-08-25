package silver.solutions.techdispatch.admin;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import silver.solutions.techdispatch.admin.dto.AdminDtos.CreateUserRequest;
import silver.solutions.techdispatch.admin.dto.AdminDtos.CreateUserResponse;
import silver.solutions.techdispatch.admin.dto.AdminDtos.InviteResponse;
import silver.solutions.techdispatch.admin.dto.AdminDtos.UpdateUserRequest;
import silver.solutions.techdispatch.admin.dto.AdminDtos.UserResponse;
import silver.solutions.techdispatch.auth.ActivationTokenService;
import silver.solutions.techdispatch.common.ApiException;
import silver.solutions.techdispatch.common.PageResponse;
import silver.solutions.techdispatch.config.TechDispatchProperties;
import silver.solutions.techdispatch.domain.AdminAuditAction;
import silver.solutions.techdispatch.domain.TechnicianProfile;
import silver.solutions.techdispatch.domain.TechnicianStatus;
import silver.solutions.techdispatch.domain.TokenPurpose;
import silver.solutions.techdispatch.domain.User;
import silver.solutions.techdispatch.domain.UserRole;
import silver.solutions.techdispatch.domain.UserStatus;
import silver.solutions.techdispatch.notification.NotificationService;
import silver.solutions.techdispatch.repository.TechnicianProfileRepository;
import silver.solutions.techdispatch.repository.UserRepository;
import silver.solutions.techdispatch.repository.UserSpecifications;

/**
 * Onboarding and offboarding of Controllers and Technicians (PRD §2 MVP scope, story M-06).
 *
 * <p>Access is restricted to the MANAGER role in {@code SecurityConfig}; per FR-01 the
 * Manager is the administrator. Every mutating method here writes an audit row in the same
 * transaction as the change itself.
 */
@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

    private final UserRepository users;
    private final TechnicianProfileRepository technicianProfiles;
    private final ActivationTokenService activationTokens;
    private final NotificationService notifications;
    private final AdminAuditService audit;
    private final TechDispatchProperties properties;

    public AdminUserService(
            UserRepository users,
            TechnicianProfileRepository technicianProfiles,
            ActivationTokenService activationTokens,
            NotificationService notifications,
            AdminAuditService audit,
            TechDispatchProperties properties) {
        this.users = users;
        this.technicianProfiles = technicianProfiles;
        this.activationTokens = activationTokens;
        this.notifications = notifications;
        this.audit = audit;
        this.properties = properties;
    }

    /**
     * @param actorId the signed-in Manager, excluded from the results — they maintain their
     *     own details through the profile menu, not by administering themselves
     */
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(
            UUID actorId, UserRole role, UserStatus status, String query, Pageable pageable) {

        Page<User> page = users.findAll(
                UserSpecifications.matching(role, status, query, actorId), pageable);

        // One query for every technician profile on the page, rather than one per row.
        Map<UUID, TechnicianStatus> technicianStatuses = technicianStatusesFor(page);

        return PageResponse.of(page, user ->
                UserResponse.from(user, technicianStatuses.get(user.getId())));
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID id) {
        User user = require(id);
        return UserResponse.from(user, technicianStatusOf(user));
    }

    /**
     * Creates a Controller or Technician in {@link UserStatus#PENDING_ACTIVATION} and issues
     * an activation link. No password is set here: the Manager never sees or transmits one.
     */
    @Transactional
    public CreateUserResponse create(UUID actorId, CreateUserRequest request) {
        UserRole role = request.role();

        if (users.existsByEmailIgnoreCase(request.email())) {
            throw ApiException.conflict("A user with that email address already exists.");
        }

        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(request.email().trim());
        user.setPhone(blankToNull(request.phone()));
        user.setAddress(blankToNull(request.address()));
        user.setRole(role);
        user.setStatus(UserStatus.PENDING_ACTIVATION);
        user.setCreatedBy(actorId);
        users.save(user);

        if (role == UserRole.TECHNICIAN) {
            technicianProfiles.save(TechnicianProfile.forUser(user.getId()));
        }

        audit.record(actorId, AdminAuditAction.USER_CREATED, user.getId(),
                Map.of("email", user.getEmail(), "role", role.name()));

        String activationUrl = issueInvite(user);
        log.info("User {} onboarded as {} by {}", user.getEmail(), role, actorId);

        return new CreateUserResponse(
                UserResponse.from(user, technicianStatusOf(user)), activationUrl);
    }

    @Transactional
    public UserResponse update(UUID actorId, UUID id, UpdateUserRequest request) {
        User user = require(id);
        UserRole newRole = request.role();

        // A Manager editing themselves here could demote away the last administrator. They
        // maintain their own name, phone and address through the profile menu instead.
        if (user.getId().equals(actorId)) {
            throw ApiException.badRequest(
                    "Edit your own details from your profile menu, not the user list.");
        }

        Map<String, Object> changes = new LinkedHashMap<>();
        if (!user.getName().equals(request.name().trim())) {
            changes.put("name", Map.of("from", user.getName(), "to", request.name().trim()));
            user.setName(request.name().trim());
        }

        String phone = blankToNull(request.phone());
        if (!Objects.equals(user.getPhone(), phone)) {
            changes.put("phone", Map.of("from", String.valueOf(user.getPhone()),
                    "to", String.valueOf(phone)));
            user.setPhone(phone);
        }

        // Only a Manager reaches this method at all, which is what enforces "the admin is the
        // only one able to change an address".
        String address = blankToNull(request.address());
        if (!Objects.equals(user.getAddress(), address)) {
            changes.put("address", Map.of("from", String.valueOf(user.getAddress()),
                    "to", String.valueOf(address)));
            user.setAddress(address);
        }

        boolean roleChanged = user.getRole() != newRole;
        UserRole previousRole = user.getRole();
        if (roleChanged) {
            changes.put("role", Map.of("from", previousRole.name(), "to", newRole.name()));
            user.setRole(newRole);
            // A role change alters what the token's authorities should be, so the old token
            // must not keep working with the old role for the rest of its 8 hours.
            user.setTokenVersion(user.getTokenVersion() + 1);
        }

        users.save(user);

        if (roleChanged) {
            syncTechnicianProfile(user, previousRole);
            audit.record(actorId, AdminAuditAction.USER_ROLE_CHANGED, user.getId(), changes);
        } else if (!changes.isEmpty()) {
            audit.record(actorId, AdminAuditAction.USER_UPDATED, user.getId(), changes);
        }

        return UserResponse.from(user, technicianStatusOf(user));
    }

    /**
     * Offboards a user (M-06). This is a soft-disable: the row stays, because jobs and audit
     * history point at it and FR-09 forbids destroying that trail.
     */
    @Transactional
    public UserResponse deactivate(UUID actorId, UUID id) {
        User user = require(id);

        if (user.getId().equals(actorId)) {
            throw ApiException.badRequest("You cannot deactivate your own account.");
        }
        if (user.getStatus() == UserStatus.DISABLED) {
            return UserResponse.from(user, technicianStatusOf(user));
        }

        user.setStatus(UserStatus.DISABLED);
        // Without this the user's existing token would keep working for up to 8 more hours.
        user.setTokenVersion(user.getTokenVersion() + 1);
        users.save(user);

        technicianProfiles.findById(user.getId()).ifPresent(profile -> {
            profile.setStatus(TechnicianStatus.OFFLINE);
            technicianProfiles.save(profile);
        });

        audit.record(actorId, AdminAuditAction.USER_DEACTIVATED, user.getId(),
                Map.of("email", user.getEmail()));

        // Their session was just revoked mid-use; tell them why rather than leaving them to
        // discover it at the next sign-in.
        notifications.sendAccountDeactivated(user);

        log.info("User {} deactivated by {}", user.getEmail(), actorId);
        return UserResponse.from(user, technicianStatusOf(user));
    }

    /**
     * Brings an offboarded user back. They return to {@code PENDING_ACTIVATION} if they never
     * set a password, otherwise straight to {@code ACTIVE} with their old credentials intact.
     */
    @Transactional
    public UserResponse reactivate(UUID actorId, UUID id) {
        User user = require(id);

        if (user.getStatus() != UserStatus.DISABLED) {
            return UserResponse.from(user, technicianStatusOf(user));
        }

        user.setStatus(user.getPasswordHash() == null
                ? UserStatus.PENDING_ACTIVATION
                : UserStatus.ACTIVE);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        users.save(user);

        audit.record(actorId, AdminAuditAction.USER_REACTIVATED, user.getId(),
                Map.of("email", user.getEmail(), "restoredTo", user.getStatus().name()));

        // A reinstated user who never activated still needs their invite link, so send that
        // rather than a sign-in prompt they cannot yet act on.
        if (user.getStatus() == UserStatus.PENDING_ACTIVATION) {
            issueInvite(user);
        } else {
            notifications.sendAccountReactivated(user, signInUrl());
        }

        log.info("User {} reactivated by {}", user.getEmail(), actorId);
        return UserResponse.from(user, technicianStatusOf(user));
    }

    /** Issues a fresh invite, which also invalidates whatever link was sent previously. */
    @Transactional
    public InviteResponse resendInvite(UUID actorId, UUID id) {
        User user = require(id);

        if (user.getStatus() == UserStatus.DISABLED) {
            throw ApiException.badRequest("Reactivate this account before inviting them again.");
        }
        if (user.getStatus() == UserStatus.ACTIVE) {
            throw ApiException.badRequest(
                    "This user has already activated. Ask them to use 'forgot password' instead.");
        }

        audit.record(actorId, AdminAuditAction.INVITE_RESENT, user.getId(),
                Map.of("email", user.getEmail()));

        return new InviteResponse(issueInvite(user));
    }

    private String signInUrl() {
        String base = properties.appBaseUrl();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + "/login";
    }

    private String issueInvite(User user) {
        String token = activationTokens.issue(user.getId(), TokenPurpose.ACTIVATION);
        String url = activationTokens.activationUrl(token);
        notifications.sendActivationInvite(user, url);
        return url;
    }

    /**
     * Technicians need a profile row for the dispatch availability list (FR-04); other roles
     * do not. Keep the two in step when a role changes rather than leaving an orphan.
     */
    private void syncTechnicianProfile(User user, UserRole previousRole) {
        if (user.getRole() == UserRole.TECHNICIAN) {
            if (technicianProfiles.findById(user.getId()).isEmpty()) {
                technicianProfiles.save(TechnicianProfile.forUser(user.getId()));
            }
        } else if (previousRole == UserRole.TECHNICIAN) {
            // Park the profile offline rather than deleting it: dispatch_responses and jobs
            // may still reference this user's history as a technician.
            technicianProfiles.findById(user.getId()).ifPresent(profile -> {
                profile.setStatus(TechnicianStatus.OFFLINE);
                technicianProfiles.save(profile);
            });
        }
    }

    private Map<UUID, TechnicianStatus> technicianStatusesFor(Page<User> page) {
        var technicianIds = page.getContent().stream()
                .filter(user -> user.getRole() == UserRole.TECHNICIAN)
                .map(User::getId)
                .toList();

        Map<UUID, TechnicianStatus> statuses = new HashMap<>();
        if (!technicianIds.isEmpty()) {
            technicianProfiles.findAllById(technicianIds)
                    .forEach(profile -> statuses.put(profile.getUserId(), profile.getStatus()));
        }
        return statuses;
    }

    private TechnicianStatus technicianStatusOf(User user) {
        if (user.getRole() != UserRole.TECHNICIAN) {
            return null;
        }
        return technicianProfiles.findById(user.getId())
                .map(TechnicianProfile::getStatus)
                .orElse(null);
    }

    private User require(UUID id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
