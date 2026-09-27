package silver.solutions.techconnect.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds the {@code techconnect.*} block of application.properties. */
@ConfigurationProperties(prefix = "techconnect")
public record TechConnectProperties(
        Jwt jwt,
        /** Base URL of the frontend. Activation and reset links are built against this. */
        String appBaseUrl,
        Activation activation,
        Login login,
        BootstrapAdmin bootstrapAdmin,
        Mail mail,
        Aws aws) {

    public record Jwt(String secret, Duration expiry, String issuer) {}

    public record Activation(Duration tokenTtl) {}

    /** Brute-force protection for POST /auth/login (§9.2). */
    public record Login(int maxFailedAttempts, Duration lockDuration) {}

    /**
     * First-run Manager. Without this the system is unusable on a fresh database: only a
     * Manager can create users, and there is no Manager to log in as.
     */
    public record BootstrapAdmin(String email, String name) {}

    public record Mail(String from) {}

    /**
     * Phase 2 (photo/document storage, PRD §7) AWS wiring. {@code endpointOverride} is blank in
     * every real environment — the SDK then talks to actual AWS — and is set to LocalStack's
     * {@code http://localhost:4566} only in local/dev config.
     */
    public record Aws(String region, String endpointOverride, String documentsBucket) {}
}
