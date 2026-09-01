package silver.solutions.techdispatch.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import silver.solutions.techdispatch.dto.request.admin.CreateUserRequest;
import silver.solutions.techdispatch.dto.request.admin.UpdateUserRequest;
import silver.solutions.techdispatch.dto.response.admin.CreateUserResponse;
import silver.solutions.techdispatch.dto.response.admin.InviteResponse;
import silver.solutions.techdispatch.dto.response.admin.UserResponse;
import silver.solutions.techdispatch.dto.response.common.PageResponse;
import silver.solutions.techdispatch.entity.UserRole;
import silver.solutions.techdispatch.entity.UserStatus;
import silver.solutions.techdispatch.security.AuthenticatedUser;
import silver.solutions.techdispatch.service.AdminUserService;

/**
 * Admin portal endpoints (M-06). MANAGER-only — enforced centrally by the
 * {@code /v1/admin/**} rule in {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/v1/admin/users")
public class AdminUserController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminUserService adminUsers;

    public AdminUserController(AdminUserService adminUsers) {
        this.adminUsers = adminUsers;
    }

    /** Filtering and paging happen in the database, not the browser. */
    /** The signed-in Manager is excluded — they edit themselves from the profile menu. */
    @GetMapping
    public PageResponse<UserResponse> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var pageable = PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        return adminUsers.list(principal.id(), role, status, q, pageable);
    }

    @PostMapping
    public ResponseEntity<CreateUserResponse> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminUsers.create(principal.id(), request));
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable UUID id) {
        return adminUsers.get(id);
    }

    @PutMapping("/{id}")
    public UserResponse update(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {
        return adminUsers.update(principal.id(), id, request);
    }

    /** Offboarding: a soft-disable that also revokes the user's current session. */
    @PostMapping("/{id}/deactivate")
    public UserResponse deactivate(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        return adminUsers.deactivate(principal.id(), id);
    }

    @PostMapping("/{id}/reactivate")
    public UserResponse reactivate(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        return adminUsers.reactivate(principal.id(), id);
    }

    @PostMapping("/{id}/resend-invite")
    public InviteResponse resendInvite(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        return adminUsers.resendInvite(principal.id(), id);
    }
}
