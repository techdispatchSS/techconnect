package silver.solutions.techdispatch.security;

import java.util.List;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;
import silver.solutions.techdispatch.entity.User;
import silver.solutions.techdispatch.repository.UserRepository;

/**
 * Turns a validated JWT into an authentication, re-checking live account state on the way.
 *
 * <p>Signature and expiry alone are not enough. A token is valid for 8 hours (FR-01), so
 * without this check a Manager offboarding a technician would have no effect until the
 * following morning. Each request therefore costs one indexed primary-key lookup — at the
 * MVP's scale of 20 concurrent technicians (§9.1) that is negligible, and it is what makes
 * "revoke access" actually mean revoke access.
 */
@Component
public class TechDispatchJwtAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository users;

    public TechDispatchJwtAuthenticationConverter(UserRepository users) {
        this.users = users;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID userId = parseSubject(jwt);

        User user = users.findById(userId)
                .orElseThrow(() -> new InvalidBearerTokenException("Unknown subject"));

        if (!user.isActive()) {
            throw new InvalidBearerTokenException("Account is not active");
        }

        if (tokenVersionOf(jwt) != user.getTokenVersion()) {
            throw new InvalidBearerTokenException("Token has been superseded");
        }

        AuthenticatedUser principal = new AuthenticatedUser(
                user.getId(), user.getEmail(), user.getName(), user.getRole());

        // "ROLE_" prefix so hasRole('MANAGER') and @PreAuthorize work as written.
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));

        return new UsernamePasswordAuthenticationToken(principal, jwt, authorities);
    }

    private static UUID parseSubject(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidBearerTokenException("Malformed subject claim");
        }
    }

    /** The {@code tv} claim deserialises as a {@link Number} whose concrete type varies. */
    private static int tokenVersionOf(Jwt jwt) {
        Object claim = jwt.getClaim("tv");
        if (claim instanceof Number number) {
            return number.intValue();
        }
        throw new InvalidBearerTokenException("Missing token version claim");
    }
}
