package silver.solutions.techdispatch.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import silver.solutions.techdispatch.dto.request.auth.ActivateRequest;
import silver.solutions.techdispatch.dto.request.auth.ChangePasswordRequest;
import silver.solutions.techdispatch.dto.request.auth.ForgotPasswordRequest;
import silver.solutions.techdispatch.dto.request.auth.LoginRequest;
import silver.solutions.techdispatch.dto.request.auth.ResetPasswordRequest;
import silver.solutions.techdispatch.dto.request.auth.UpdateProfileRequest;
import silver.solutions.techdispatch.dto.response.auth.LoginResponse;
import silver.solutions.techdispatch.dto.response.auth.ProfileResponse;
import silver.solutions.techdispatch.security.AuthenticatedUser;
import silver.solutions.techdispatch.service.AuthService;

/** PRD §8.1 plus the activation and password-recovery endpoints. */
@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * The caller's own record. The frontend calls this on boot to confirm a restored session
     * is still good: a token that survived an offboarding or password change is rejected here
     * rather than surfacing as a failure on some later screen.
     */
    @GetMapping("/me")
    public ProfileResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return authService.profile(principal.id());
    }

    /** Self-service profile edit. Address changes are honoured for Managers only. */
    @PutMapping("/me")
    public ProfileResponse updateMe(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        return authService.updateProfile(principal.id(), request);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request.email(), request.password(), http.getRemoteAddr());
    }

    @PostMapping("/activate")
    public ResponseEntity<Void> activate(@Valid @RequestBody ActivateRequest request) {
        authService.activate(request.token(), request.password());
        return ResponseEntity.noContent().build();
    }

    /** Always 202, so the response cannot be used to discover which emails are registered. */
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.password());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(
                principal.id(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
