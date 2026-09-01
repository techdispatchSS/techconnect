package silver.solutions.techdispatch;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import silver.solutions.techdispatch.service.NotificationService;

/**
 * Smoke test: the context must wire up and Flyway must migrate cleanly against a real
 * PostgreSQL, which is also what validates every JPA mapping against the actual schema.
 */
class TechdispatchApplicationTests extends AbstractPostgresIntegrationTest {

	/** Stubbed so the context never opens an SMTP connection during tests. */
	@MockitoBean private NotificationService notifications;

	@Test
	void contextLoads() {
	}

}
