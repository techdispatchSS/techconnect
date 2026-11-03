package silver.solutions.techconnect.integration.push;

import com.google.auth.oauth2.GoogleCredentials;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Sends through the FCM HTTP v1 API. Called right after a dispatch commits, so it fails fast
 * rather than holding up the controller's request, and it never throws: a missed push must not
 * look like a failed dispatch, and the technician still gets the email.
 */
@Component
@ConditionalOnExpression(FcmPushSender.CONFIGURED)
class FcmPushSender implements PushSender {

    /** Read through the environment: pasted into the expression, a quote in the key would break it. */
    static final String CONFIGURED =
            "!environment.getProperty('techconnect.push.project-id', '').isBlank()"
                    + " and !environment.getProperty('techconnect.push.service-account-json', '').isBlank()";
    static final String NOT_CONFIGURED = "!(" + CONFIGURED + ")";

    private static final Logger log = LoggerFactory.getLogger(FcmPushSender.class);

    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    /** An offer lapses after 10 minutes, so there is no point delivering it to a phone later. */
    private static final int TIME_TO_LIVE_SECONDS = 600;

    private final RestClient http;
    private final Supplier<String> accessToken;
    private final String sendPath;

    @Autowired
    FcmPushSender(PushProperties properties) {
        this(RestClient.builder().baseUrl("https://fcm.googleapis.com").requestFactory(timeouts()),
                credentials(properties.serviceAccountJson()),
                properties.projectId());
    }

    FcmPushSender(RestClient.Builder builder, Supplier<String> accessToken, String projectId) {
        this.http = builder.build();
        this.accessToken = accessToken;
        this.sendPath = "/v1/projects/" + projectId + "/messages:send";
    }

    @Override
    public Result send(String token, PushMessage message) {
        try {
            http.post()
                    .uri(sendPath)
                    .header("Authorization", "Bearer " + accessToken.get())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request(token, message))
                    .retrieve()
                    .toBodilessEntity();
            return Result.SENT;
        } catch (HttpClientErrorException.NotFound e) {
            // FCM's UNREGISTERED: the only answer that means this token will never work again.
            return Result.DEAD_TOKEN;
        } catch (RestClientException | UncheckedIOException e) {
            log.warn("Could not send a push: {}", e.getMessage());
            return Result.FAILED;
        }
    }

    /**
     * Data only, so the service worker draws the notification itself and the same shape works
     * whatever the browser. High urgency, or a phone in its pocket may hold it back.
     */
    private static Map<String, Object> request(String token, PushMessage message) {
        return Map.of("message", Map.of(
                "token", token,
                "data", Map.of(
                        "title", message.title(),
                        "body", message.body(),
                        "url", message.url()),
                "webpush", Map.of("headers", Map.of(
                        "Urgency", "high",
                        "TTL", String.valueOf(TIME_TO_LIVE_SECONDS)))));
    }

    private static Supplier<String> credentials(String serviceAccountJson) {
        GoogleCredentials credentials;
        try {
            credentials = GoogleCredentials
                    .fromStream(new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8)))
                    .createScoped(List.of(SCOPE));
        } catch (IOException e) {
            // Bad configuration, found at startup rather than on the first dispatch.
            throw new IllegalStateException("techconnect.push.service-account-json is not a valid key", e);
        }
        return () -> {
            try {
                credentials.refreshIfExpired();
                return credentials.getAccessToken().getTokenValue();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }
}
