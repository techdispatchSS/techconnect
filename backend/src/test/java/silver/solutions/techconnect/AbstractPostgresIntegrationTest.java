package silver.solutions.techconnect;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base for integration tests that need the real schema.
 *
 * <p>The container is {@code static} and never explicitly stopped so that all subclasses share
 * one database and one Flyway run, rather than paying container startup per test class.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Testcontainers
public abstract class AbstractPostgresIntegrationTest {

    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("techconnect")
                    .withUsername("techconnect")
                    .withPassword("techconnect");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        // Deterministic test config, independent of the developer's environment.
        registry.add("techconnect.jwt.secret",
                () -> "integration-test-signing-secret-at-least-32-bytes");
        registry.add("techconnect.app-base-url", () -> "http://localhost:4200");
        // Disabled so each test creates its own manager and the tests stay independent.
        registry.add("techconnect.bootstrap-admin.email", () -> "");
    }
}
