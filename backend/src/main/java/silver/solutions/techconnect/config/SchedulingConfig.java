package silver.solutions.techconnect.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on the background jobs: the dispatch-expiry sweep and the Freshdesk poll. Switchable so
 * the integration tests, which drive those services directly and need deterministic state, are
 * not racing a timer.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "techconnect.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
