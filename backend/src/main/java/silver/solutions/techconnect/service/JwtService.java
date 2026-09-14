package silver.solutions.techconnect.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;
import silver.solutions.techconnect.config.TechConnectProperties;
import silver.solutions.techconnect.entity.User;

/**
 * Mints the self-issued JWTs described in PRD §8.1. There is no external identity
 * provider — TechConnect signs its own tokens with a shared HMAC secret.
 */
@Service
public class JwtService {

    /** HS256 needs a key of at least 256 bits; anything shorter is rejected outright. */
    private static final int MIN_SECRET_BYTES = 32;

    /**
     * Retired placeholder values that once shipped as working defaults in
     * {@code application.properties} / {@code .env.example}. Both files now leave the
     * property unset so a missing secret fails on length alone, but this list is a second,
     * independent tripwire: if either string is ever pasted into a real deployment (an old
     * `.env` recovered from backup, a value copied from git history, a stale CI secret),
     * startup fails instead of silently signing tokens with a value published in source
     * control.
     */
    private static final Set<String> RETIRED_PLACEHOLDER_SECRETS = Set.of(
            "local-dev-only-secret-change-me-at-least-32-bytes-long");

    private final JwtEncoder encoder;
    private final TechConnectProperties properties;

    public JwtService(JwtEncoder encoder, TechConnectProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    public static SecretKey secretKey(String secret) {
        if (RETIRED_PLACEHOLDER_SECRETS.contains(secret)) {
            throw new IllegalStateException(
                    "techconnect.jwt.secret is set to a retired placeholder value that was "
                            + "once a public default in this repository. It must never be used "
                            + "for real tokens. Generate a fresh one with: openssl rand -base64 48");
        }

        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "techconnect.jwt.secret must be at least " + MIN_SECRET_BYTES
                            + " bytes for HS256; got " + bytes.length
                            + ". Generate one with: openssl rand -base64 48");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    /** Issues an 8-hour token for a freshly authenticated user (FR-01, §9.2). */
    public IssuedToken issue(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.jwt().expiry());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("name", user.getName())
                .claim("role", user.getRole().name())
                // Lets the auth filter detect a token that predates an offboarding or
                // password change and refuse it, rather than honouring it for 8 hours.
                .claim("tv", user.getTokenVersion())
                .build();

        String value = encoder
                .encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        return new IssuedToken(value, expiresAt);
    }

    public record IssuedToken(String value, Instant expiresAt) {}
}
