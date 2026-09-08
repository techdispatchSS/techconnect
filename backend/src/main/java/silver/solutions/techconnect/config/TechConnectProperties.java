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
        Mail mail) {

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
}
