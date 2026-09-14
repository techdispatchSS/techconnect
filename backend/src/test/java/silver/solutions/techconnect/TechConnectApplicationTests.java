package silver.solutions.techconnect;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import silver.solutions.techconnect.service.NotificationService;

/**
 * Smoke test: the context must wire up and Flyway must migrate cleanly against a real
 * PostgreSQL, which is also what validates every JPA mapping against the actual schema.
 */
class TechConnectApplicationTests extends AbstractPostgresIntegrationTest {

	/** Stubbed so the context never opens an SMTP connection during tests. */
	@MockitoBean private NotificationService notifications;

	@Test
	void contextLoads() {
	}

}
