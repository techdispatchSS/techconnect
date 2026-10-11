package silver.solutions.techconnect.integration.freshdesk;

import java.time.Instant;
import java.util.List;

/**
 * Where tickets come from. The sync reads through this and nothing else, so the simulated
 * Freshdesk and a real one (Freshdesk's {@code GET /api/v2/tickets?updated_since=...}) are
 * interchangeable without touching the import logic.
 *
 * <p>Only the simulated implementation exists: the owners have not yet confirmed the Freshdesk
 * account, API access, or which custom fields carry the site address.
 */
public interface FreshdeskClient {

    /** Tickets created or changed at or after {@code since}, oldest first. */
    List<FreshdeskTicket> ticketsUpdatedSince(Instant since);
}
