package silver.solutions.techconnect.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Loads {@code .env} (repo root, {@code POSTGRES_PORT=...} style — see {@code .env.example})
 * into the Spring {@link org.springframework.core.env.Environment} for local runs.
 *
 * <p>Nothing else in the project reads this file for the backend process: {@code
 * docker-compose.yml} consumes it for the Postgres/Mailpit/Localstack containers only,
 * `./mvnw spring-boot:run` never sees it unless the values are exported into the shell first.
 * That gap has repeatedly looked like a bug — e.g. {@link BootstrapAdminRunner} silently
 * skipping because {@code techconnect.bootstrap-admin.email} reads as unset even though
 * {@code .env} has it — when the real cause was just that this file was never loaded.
 *
 * <p>Registered via {@code META-INF/spring.factories} (not the newer {@code
 * META-INF/spring/*.imports} convention — {@code EnvironmentPostProcessor} discovery in this
 * Spring Boot version only picks up the classic {@code spring.factories} entry; an
 * {@code .imports} file for it is silently never invoked). Added with the lowest precedence: a
 * real environment variable or {@code -D} system property always wins over {@code .env},
 * matching every other dotenv tool's convention and keeping this inert wherever {@code .env}
 * does not exist — production included, since nothing there ships that file.
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path dotenv = locate();
        if (dotenv == null) {
            return;
        }

        Map<String, Object> values = parse(dotenv);
        if (!values.isEmpty()) {
            environment.getPropertySources().addLast(new MapPropertySource("dotenv", values));
        }
    }

    /**
     * `./mvnw spring-boot:run` is normally run from {@code backend/}, one level below the
     * repo root where {@code .env} lives, so both the working directory and its parent are
     * checked.
     */
    private Path locate() {
        for (Path candidate : List.of(Path.of(".env"), Path.of("../.env"))) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private Map<String, Object> parse(Path file) {
        Map<String, Object> values = new LinkedHashMap<>();
        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException e) {
            return values;
        }

        for (String line : lines) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            int separator = trimmed.indexOf('=');
            if (separator <= 0) {
                continue;
            }

            String key = trimmed.substring(0, separator).strip();
            String value = trimmed.substring(separator + 1).strip();
            if (value.length() >= 2
                    && ((value.startsWith("\"") && value.endsWith("\""))
                            || (value.startsWith("'") && value.endsWith("'")))) {
                value = value.substring(1, value.length() - 1);
            }

            values.put(key, value);
        }

        return values;
    }
}
