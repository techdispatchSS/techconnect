package silver.solutions.techdispatch.auth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import silver.solutions.techdispatch.common.ApiException;
import silver.solutions.techdispatch.config.TechDispatchProperties;
import silver.solutions.techdispatch.domain.TokenPurpose;
import silver.solutions.techdispatch.domain.UserActivationToken;
import silver.solutions.techdispatch.repository.UserActivationTokenRepository;

/**
 * Issues and redeems the single-use links behind account activation and password reset.
 *
 * <p>The raw token is generated here, handed straight to the caller, and never stored —
 * only its SHA-256 hash is persisted. A leaked database therefore yields no working links.
 * Because the value is unguessable and single-use, the Manager onboarding a user never
 * handles a password at all.
 */
@Service
public class ActivationTokenService {

    /** 256 bits of entropy — not brute-forceable, and short enough to sit in a URL. */
    private static final int TOKEN_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserActivationTokenRepository tokens;
    private final TechDispatchProperties properties;

    public ActivationTokenService(
            UserActivationTokenRepository tokens, TechDispatchProperties properties) {
        this.tokens = tokens;
        this.properties = properties;
    }

    /**
     * Mints a fresh token and invalidates any outstanding one of the same purpose, so that
     * "resend invite" kills the previously mailed link instead of leaving both live.
     *
     * @return the raw token — the only time it exists in readable form
     */
    @Transactional
    public String issue(UUID userId, TokenPurpose purpose) {
        tokens.consumeOutstanding(userId, purpose);

        byte[] raw = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        UserActivationToken entity = new UserActivationToken();
        entity.setUserId(userId);
        entity.setTokenHash(hash(token));
        entity.setPurpose(purpose);
        entity.setExpiresAt(Instant.now().plus(properties.activation().tokenTtl()));
        tokens.save(entity);

        return token;
    }

    /**
     * Validates and burns a token.
     *
     * <p>Unknown, expired, already-used and wrong-purpose tokens all fail with the same
     * message: distinguishing them would tell an attacker which guesses were near misses.
     *
     * @return the id of the user the token belongs to
     */
    @Transactional
    public UUID consume(String token, TokenPurpose purpose) {
        UserActivationToken entity = tokens.findByTokenHash(hash(token))
                .filter(t -> t.getPurpose() == purpose)
                .filter(UserActivationToken::isUsable)
                .orElseThrow(() -> ApiException.badRequest(
                        "This link is invalid or has expired. Request a new one."));

        entity.setConsumedAt(Instant.now());
        tokens.save(entity);
        return entity.getUserId();
    }

    public String activationUrl(String token) {
        return link("/activate", token);
    }

    public String passwordResetUrl(String token) {
        return link("/reset-password", token);
    }

    /** Links point at the frontend, which then calls the API — not at the API itself. */
    private String link(String path, String token) {
        String base = properties.appBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
