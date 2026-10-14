package silver.solutions.techconnect.service;

import java.time.Instant;
import java.util.UUID;
import silver.solutions.techconnect.entity.IncidentPriority;
import silver.solutions.techconnect.entity.User;

/**
 * Outbound notifications (PRD FR-08).
 *
 * <p>This interface exists now, ahead of the SendGrid work, so the admin portal is not
 * blocked on an external provider. The local implementation delivers to Mailpit; the
 * SendGrid implementation (§7 — "SendGrid API (MVP)") drops in behind the same contract
 * without touching a single caller.
 */
public interface NotificationService {

    /** Invites a newly onboarded Controller or Technician to set their password. */
    void sendActivationInvite(User user, String activationUrl);

    void sendPasswordReset(User user, String resetUrl);

    /** Tells an offboarded user their access has ended, so it is not a silent lockout. */
    void sendAccountDeactivated(User user);

    /** Tells a reinstated user they can sign in again. */
    void sendAccountReactivated(User user, String signInUrl);

    /** Confirms to an applicant (who has no account) that their application was received. */
    void sendApplicationReceived(String email, String firstName);

    /** Tells an applicant their application was not successful and cannot be resubmitted. */
    void sendApplicationRejected(String email, String firstName);

    /** What a technician needs to know about a job that has been sent to them. */
    record DispatchNotice(
            String ticketRef,
            String clientName,
            String siteAddress,
            String issueType,
            IncidentPriority priority,
            String notes,
            Instant expiresAt,
            /** True when this is a repeat of an earlier notification, not the first. */
            boolean reminder,
            /** The dispatch this answers, so the email's buttons can point at it. May be null. */
            UUID dispatchId) {}

    /**
     * Tells a technician a job has been sent to them and is waiting for their answer. Sent when
     * the dispatch is created, and again each time the Controller resends it.
     */
    void sendDispatchNotice(User technician, DispatchNotice notice);
}
