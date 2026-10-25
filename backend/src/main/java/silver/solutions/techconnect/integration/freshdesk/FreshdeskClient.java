package silver.solutions.techconnect.integration.freshdesk;

import java.time.Instant;
import java.util.List;

/**
 * Where tickets come from. The sync reads through this and nothing else, so the simulated
 * Freshdesk and a real one (Freshdesk's {@code GET /api/v2/tickets?updated_since=...}) are
 * interchangeable without touching the import logic.
 *
 * <p>Two implementations: {@link SimulatedFreshdeskClient} for demos, and {@link LiveFreshdeskClient}
 * for the real account (read-only). Which custom fields carry the site address is still to be
 * confirmed with the owners; the real account has none today.
 */
public interface FreshdeskClient {

    /** Tickets created or changed at or after {@code since}, oldest first. */
    List<FreshdeskTicket> ticketsUpdatedSince(Instant since);
}
