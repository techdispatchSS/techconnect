package silver.solutions.techconnect.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import silver.solutions.techconnect.security.StompAuthInterceptor;

/**
 * STOMP over WebSocket at {@code /api/ws} (#16), for pages served from the app's own address. That
 * is named outright rather than left to Spring's same-origin check, which compares against what
 * the proxies forward and fails behind TLS termination. The in-memory broker reaches only this
 * instance's sockets: with more than one backend running, a screen connected to another instance
 * gets the change at its next poll instead, which is why the screens keep polling.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** A client must keep the line alive this often, as we do, or proxies drop an idle socket. */
    private static final long HEARTBEAT_MS = 10_000;
    /** Clients only send CONNECT and SUBSCRIBE, and nothing else is accepted. */
    private static final int MAX_INBOUND_BYTES = 16 * 1024;

    private final StompAuthInterceptor authInterceptor;
    private final TechConnectProperties properties;
    private TaskScheduler heartbeatScheduler;

    public WebSocketConfig(StompAuthInterceptor authInterceptor, TechConnectProperties properties) {
        this.authInterceptor = authInterceptor;
        this.properties = properties;
    }

    @Autowired
    void setHeartbeatScheduler(
            @Lazy @Qualifier("messageBrokerTaskScheduler") TaskScheduler heartbeatScheduler) {
        this.heartbeatScheduler = heartbeatScheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(appOrigin());
    }

    /** An origin has no trailing slash, which the configured address might. */
    private String appOrigin() {
        return properties.appBaseUrl().trim().replaceAll("/+$", "");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[] {HEARTBEAT_MS, HEARTBEAT_MS})
                .setTaskScheduler(heartbeatScheduler);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(MAX_INBOUND_BYTES);
    }
}
