package silver.solutions.techconnect.dto.response.controller;

import java.time.Instant;

/**
 * The outcome of resending a dispatch notification.
 *
 * @param notified          how many technicians were told again (those who had not answered)
 * @param notificationCount total notifications for this dispatch so far, the first included
 */
public record ResendDispatchResponse(int notified, int notificationCount, Instant lastNotifiedAt) {
}
