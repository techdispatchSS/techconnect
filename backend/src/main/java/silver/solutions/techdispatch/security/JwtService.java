package silver.solutions.techdispatch.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;
import silver.solutions.techdispatch.config.TechDispatchProperties;
import silver.solutions.techdispatch.domain.User;

/**
 * Mints the self-issued JWTs described in PRD §8.1. There is no external identity
 * provider — TechDispatch signs its own tokens with a shared HMAC secret.
 */
@Service
public class JwtService {

    /** HS256 needs a key of at least 256 bits; anything shorter is rejected outright. */
    private static final int MIN_SECRET_BYTES = 32;

    private final JwtEncoder encoder;
    private final TechDispatchProperties properties;

    public JwtService(JwtEncoder encoder, TechDispatchProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    public static SecretKey secretKey(String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "techdispatch.jwt.secret must be at least " + MIN_SECRET_BYTES
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
