package silver.solutions.techconnect.service;

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
}
