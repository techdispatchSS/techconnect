package silver.solutions.techconnect.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SubmissionThrottleTest {

    /** A clock the test moves by hand. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void allowsUpToTheLimitThenBlocksThatAddressOnly() {
        var throttle = new SubmissionThrottle(3, 100, Duration.ofHours(1), new MutableClock());

        for (int i = 0; i < 3; i++) {
            assertThat(throttle.acquire("1.1.1.1")).isZero();
        }
        assertThat(throttle.acquire("1.1.1.1")).as("fourth attempt").isPositive();
        assertThat(throttle.acquire("2.2.2.2")).as("a different address is unaffected").isZero();
    }

    @Test
    void theWindowRollsSoAnAddressIsAllowedAgainLater() {
        var clock = new MutableClock();
        var throttle = new SubmissionThrottle(1, 100, Duration.ofHours(1), clock);

        assertThat(throttle.acquire("1.1.1.1")).isZero();
        long wait = throttle.acquire("1.1.1.1");
        assertThat(wait).isBetween(1L, 3600L);

        clock.advance(Duration.ofHours(1).plusSeconds(1));
        assertThat(throttle.acquire("1.1.1.1")).isZero();
    }

    @Test
    void aGlobalCeilingStopsADistributedFloodEvenFromManyAddresses() {
        var throttle = new SubmissionThrottle(5, 4, Duration.ofHours(1), new MutableClock());

        for (int i = 0; i < 4; i++) {
            assertThat(throttle.acquire("10.0.0." + i)).isZero();
        }
        assertThat(throttle.acquire("10.0.0.99")).isPositive();
    }
}
