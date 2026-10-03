package silver.solutions.techconnect.security;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import silver.solutions.techconnect.config.TechConnectProperties;

/**
 * Rolling-window limiter for the public application endpoint: at most {@code perKey} accepted
 * attempts per client address, and {@code global} across everyone, in any {@code window}.
 *
 * <p>In memory and per instance, deliberately: it is a brake on casual abuse and on storage
 * cost, not a distributed quota. Behind several instances the effective limit is simply the
 * limit times the instance count, which is still a hard ceiling.
 */
@Component
public class SubmissionThrottle {

    /** Hard cap on tracked addresses, so a flood of distinct sources cannot exhaust memory. */
    private static final int MAX_TRACKED_KEYS = 20_000;

    private final int perKey;
    private final int global;
    private final Duration window;
    private final Clock clock;

    private final Map<String, ArrayDeque<Long>> byKey = new HashMap<>();
    private final ArrayDeque<Long> all = new ArrayDeque<>();

    @Autowired
    public SubmissionThrottle(TechConnectProperties properties) {
        this(
                properties.applications().perIpPerHour(),
                properties.applications().globalPerHour(),
                Duration.ofHours(1),
                Clock.systemUTC());
    }

    public SubmissionThrottle(int perKey, int global, Duration window, Clock clock) {
        this.perKey = perKey;
        this.global = global;
        this.window = window;
        this.clock = clock;
    }

    /**
     * Records an attempt if allowed.
     *
     * @return 0 when the attempt may proceed, otherwise how many seconds until it could
     */
    public synchronized long acquire(String key) {
        long now = clock.millis();
        long cutoff = now - window.toMillis();

        evict(all, cutoff);
        if (all.size() >= global) {
            return retryAfterSeconds(all, now);
        }

        ArrayDeque<Long> hits = byKey.get(key);
        if (hits == null) {
            if (byKey.size() >= MAX_TRACKED_KEYS) {
                purgeIdle(cutoff);
                if (byKey.size() >= MAX_TRACKED_KEYS) {
                    return window.toSeconds();
                }
            }
            hits = new ArrayDeque<>();
            byKey.put(key, hits);
        }

        evict(hits, cutoff);
        if (hits.size() >= perKey) {
            return retryAfterSeconds(hits, now);
        }

        hits.addLast(now);
        all.addLast(now);
        return 0;
    }

    private void evict(ArrayDeque<Long> hits, long cutoff) {
        while (!hits.isEmpty() && hits.peekFirst() <= cutoff) {
            hits.removeFirst();
        }
    }

    private void purgeIdle(long cutoff) {
        Iterator<Map.Entry<String, ArrayDeque<Long>>> it = byKey.entrySet().iterator();
        while (it.hasNext()) {
            ArrayDeque<Long> hits = it.next().getValue();
            evict(hits, cutoff);
            if (hits.isEmpty()) {
                it.remove();
            }
        }
    }

    private long retryAfterSeconds(ArrayDeque<Long> hits, long now) {
        long oldest = hits.isEmpty() ? now : hits.peekFirst();
        return Math.max(1, (oldest + window.toMillis() - now + 999) / 1000);
    }
}
