package silver.solutions.techconnect.integration.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection to Firebase Cloud Messaging. Blank {@code projectId} or {@code serviceAccountJson}
 * means pushes aren't sent; blank web settings mean the app doesn't offer to turn alerts on.
 *
 * @param projectId the Firebase project
 * @param serviceAccountJson the service account key that may send, as JSON. A secret
 * @param web what a browser needs to register with Firebase. Public by design: these identify the
 *     project and grant nothing
 */
@ConfigurationProperties(prefix = "techconnect.push")
public record PushProperties(String projectId, String serviceAccountJson, Web web) {

    public record Web(String apiKey, String messagingSenderId, String appId, String vapidKey) {}

    /** Whether the browser side has what it needs to offer alerts. */
    public boolean webConfigured() {
        return !isBlank(projectId)
                && web != null
                && !isBlank(web.apiKey())
                && !isBlank(web.messagingSenderId())
                && !isBlank(web.appId())
                && !isBlank(web.vapidKey());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
