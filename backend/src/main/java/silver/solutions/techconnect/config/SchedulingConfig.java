package silver.solutions.techconnect.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Turns on the background jobs: the dispatch-expiry sweep and the Freshdesk poll. Switchable so
 * the integration tests, which drive those services directly and need deterministic state, are
 * not racing a timer.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "techconnect.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {

    /**
     * The jobs' own threads, found by this bean name. Without it they would land on the socket
     * broker's scheduler, where a slow call to Freshdesk could hold up the heartbeats and get
     * every socket dropped. Three, so that slow call can't hold up the expiry sweep either.
     */
    @Bean
    ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(3);
        scheduler.setThreadNamePrefix("scheduled-");
        return scheduler;
    }
}
