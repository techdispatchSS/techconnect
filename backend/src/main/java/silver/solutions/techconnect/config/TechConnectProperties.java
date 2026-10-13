package silver.solutions.techconnect.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

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
        Aws aws,
        Security security,
        Applications applications,
        Simulation simulation) {

    /**
     * Stand-ins for parties that are not connected yet, so the dashboard can be exercised and
     * user-tested end to end: a simulated Freshdesk (tickets appear on demand and flow through
     * the real sync path) and a "respond as technician" control for dispatches, since the
     * technician app does not exist. Off unless explicitly enabled — never enable it against
     * real operations, because it makes up incidents.
     */
    public record Simulation(boolean enabled) {}

    /** Null-safe: tests and callers that build the properties by hand may omit the block. */
    public boolean simulationEnabled() {
        return simulation != null && simulation.enabled();
    }

    /**
     * How many reverse proxies sit between the internet and this app and append to
     * {@code X-Forwarded-For} (0 locally; 1 behind the ALB or the compose nginx). The client
     * address is read from that many entries in from the right, so a caller cannot choose their
     * own rate-limit key by sending a forged header of their own.
     */
    public record Security(int trustedProxyHops) {}

    /** Limits for the public technician-application endpoint, which accepts file uploads. */
    public record Applications(
            int maxFiles,
            DataSize maxFileSize,
            DataSize maxRequestSize,
            int perIpPerHour,
            int globalPerHour) {}

    public record Jwt(String secret, Duration expiry, String issuer) {}

    public record Activation(Duration tokenTtl) {}

    /** Brute-force protection for POST /auth/login (§9.2). */
    public record Login(int maxFailedAttempts, Duration lockDuration) {}

    /**
     * First-run Manager. Without this the system is unusable on a fresh database: only a
     * Manager can create users, and there is no Manager to log in as.
     */
    public record BootstrapAdmin(String email, String name) {}

    /**
     * Who the notification emails come from. {@code from} must be an address on a domain verified
     * with the mail provider; {@code fromName} is the display name people see in their inbox
     * ("TechConnect" rather than a bare no-reply address); {@code replyTo} is where a person's
     * reply goes, so a question about an invite reaches somebody instead of a dead mailbox.
     * Either of the last two may be blank to leave it out.
     */
    public record Mail(String from, String fromName, String replyTo) {}

    /**
     * Phase 2 (photo/document storage, PRD §7) AWS wiring. {@code endpointOverride} is blank in
     * every real environment — the SDK then talks to actual AWS — and is set to LocalStack's
     * {@code http://localhost:4566} only in local/dev config.
     */
    public record Aws(String region, String endpointOverride, String documentsBucket) {}
}
