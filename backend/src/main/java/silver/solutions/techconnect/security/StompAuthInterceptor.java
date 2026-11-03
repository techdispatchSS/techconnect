package silver.solutions.techconnect.security;

import java.security.Principal;
import java.util.UUID;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import silver.solutions.techconnect.entity.UserRole;
import silver.solutions.techconnect.service.RealtimeEvents;

/**
 * The socket's security. A browser can't put a bearer header on a WebSocket handshake, so the
 * handshake itself is open and the token comes in the STOMP {@code CONNECT} frame, where it gets
 * the same checks as a REST call. After that a session may only subscribe to what its role is
 * for, and may not send anything: traffic on this socket only ever flows to the client.
 */
@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    private static final String BEARER = "Bearer ";
    private static final String OWN_EVENTS = "/user" + RealtimeEvents.USER_QUEUE;

    /** Named by user id, which is what {@code convertAndSendToUser} addresses. */
    record SocketUser(UUID id, UserRole role) implements Principal {
        @Override
        public String getName() {
            return id.toString();
        }
    }

    private final JwtDecoder jwtDecoder;
    private final TechConnectJwtAuthenticationConverter jwtConverter;

    public StompAuthInterceptor(
            JwtDecoder jwtDecoder, TechConnectJwtAuthenticationConverter jwtConverter) {
        this.jwtDecoder = jwtDecoder;
        this.jwtConverter = jwtConverter;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        switch (accessor.getCommand()) {
            case CONNECT, STOMP -> accessor.setUser(authenticate(accessor));
            case SUBSCRIBE -> requireAllowed(accessor);
            case SEND -> throw new MessagingException("This socket does not accept messages.");
            default -> { }
        }
        return message;
    }

    private SocketUser authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith(BEARER)) {
            throw new MessagingException("Sign in to connect.");
        }
        try {
            AbstractAuthenticationToken authentication =
                    jwtConverter.convert(jwtDecoder.decode(header.substring(BEARER.length())));
            AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
            return new SocketUser(user.id(), user.role());
        } catch (JwtException | AuthenticationException e) {
            throw new MessagingException("Sign in to connect.");
        }
    }

    private static void requireAllowed(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        boolean allowed = accessor.getUser() instanceof SocketUser user
                && (OWN_EVENTS.equals(destination)
                        || (RealtimeEvents.CONTROLLER_TOPIC.equals(destination)
                                && user.role() == UserRole.CONTROLLER));
        if (!allowed) {
            throw new MessagingException("Not allowed to subscribe to " + destination);
        }
    }
}
