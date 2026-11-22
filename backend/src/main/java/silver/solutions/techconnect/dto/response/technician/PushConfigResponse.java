package silver.solutions.techconnect.dto.response.technician;

/**
 * What the app needs to register a browser for job alerts. {@code enabled} is false when Firebase
 * isn't set up, and the app then doesn't offer alerts at all. None of this is secret.
 */
public record PushConfigResponse(
        boolean enabled,
        String apiKey,
        String projectId,
        String messagingSenderId,
        String appId,
        String vapidKey) {

    public static PushConfigResponse disabled() {
        return new PushConfigResponse(false, null, null, null, null, null);
    }
}
