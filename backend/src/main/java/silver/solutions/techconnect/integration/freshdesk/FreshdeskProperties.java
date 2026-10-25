package silver.solutions.techconnect.integration.freshdesk;

import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import silver.solutions.techconnect.entity.FreshdeskUpdate;

/**
 * Connection to the real Freshdesk. Blank {@code domain} means not connected: the live client is
 * not created and the sync stays idle (or uses the simulated Freshdesk, if that is on).
 *
 * @param domain the account's host, e.g. {@code silversolutions-help.freshdesk.com}
 * @param apiKey an agent's API key. Freshdesk keys cannot be scoped, so what may be done with it
 *     is enforced here: the reader only ever sends GET, and writes happen only in {@code LIVE}
 *     push mode, to the two ticket endpoints the sender allows
 * @param initialLookback how far back the first pass after a start reaches; without it a fresh
 *     process would page through the account's whole history
 * @param maxPagesPerSync pages of 100 tickets one pass may read; the rest wait for the next pass,
 *     so a backlog is worked through without spending the account's shared rate limit at once
 * @param push what TechConnect tells Freshdesk back about the work
 */
@ConfigurationProperties(prefix = "techconnect.freshdesk")
public record FreshdeskProperties(
        String domain, String apiKey, Duration initialLookback, int maxPagesPerSync, Push push) {

    public enum PushMode {
        /** Nothing is recorded or sent. */
        OFF,
        /** Updates are recorded and shown on the dashboard as "would send", never sent. */
        DEMO,
        /** Updates are sent to Freshdesk. Needs the live connection; demo otherwise. */
        LIVE
    }

    /**
     * @param mode {@code OFF}, {@code DEMO} (the default) or {@code LIVE}
     * @param events which kinds of update to make; others are not even recorded
     * @param interval how often the sender works through what is waiting, in live mode
     */
    public record Push(PushMode mode, Set<FreshdeskUpdate.Event> events, Duration interval) {}

    /** Null-safe, so properties built by hand (tests) may leave the push block out. */
    public PushMode pushMode() {
        return push == null || push.mode() == null ? PushMode.DEMO : push.mode();
    }

    public boolean pushes(FreshdeskUpdate.Event event) {
        return pushMode() != PushMode.OFF
                && (push == null || push.events() == null || push.events().contains(event));
    }
}
