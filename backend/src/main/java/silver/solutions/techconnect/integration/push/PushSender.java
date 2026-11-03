package silver.solutions.techconnect.integration.push;

/** Delivers one notification to one device. */
public interface PushSender {

    /** What a notification says, and where tapping it goes. */
    record PushMessage(String title, String body, String url) {}

    enum Result {
        SENT,
        /** The device has gone (app removed, alerts turned off): stop sending to this token. */
        DEAD_TOKEN,
        /** Worth trying again next time; the token is kept. */
        FAILED
    }

    Result send(String token, PushMessage message);
}
