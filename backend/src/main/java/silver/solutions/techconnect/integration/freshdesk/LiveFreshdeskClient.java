package silver.solutions.techconnect.integration.freshdesk;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import silver.solutions.techconnect.config.TechConnectProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * The real Freshdesk, read through its v2 REST API. Read-only by construction: every request goes
 * through {@link #GET_ONLY}, which refuses anything but GET, so nothing here can change a ticket's
 * status, assignment or notes in an account that people are working in.
 *
 * <p>Freshdesk's rate limit is per account and shared with everything else using it, so a pass
 * reads at most {@code maxPagesPerSync} pages, stops paging early once half the minute's budget
 * is gone, and fails outright on a 429 rather than retrying. Tickets come oldest first, so a pass
 * cut short still moves the sync's cursor correctly and the next pass carries on from there.
 */
@Component
@ConditionalOnExpression("!'${techconnect.freshdesk.domain:}'.isBlank()")
public class LiveFreshdeskClient implements FreshdeskClient {

    /** Refuses every request that is not a GET, before it leaves the process. */
    static final ClientHttpRequestInterceptor GET_ONLY = (request, body, execution) -> {
        if (request.getMethod() != HttpMethod.GET) {
            throw new IllegalStateException(
                    "Freshdesk is connected read-only; refused " + request.getMethod() + " " + request.getURI());
        }
        return execution.execute(request, body);
    };

    private static final int PAGE_SIZE = 100;
    private static final double RESERVE_FRACTION = 0.5;

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final RestClient http;
    private final FreshdeskProperties properties;
    private final Clock clock;
    /** Company id to name. A company's name is not on the ticket list, and rarely changes. */
    private final Map<Long, Optional<String>> companies = new ConcurrentHashMap<>();

    @Autowired
    public LiveFreshdeskClient(FreshdeskProperties properties, TechConnectProperties techConnect) {
        this(RestClient.builder().requestFactory(timeouts()), properties, techConnect, Clock.systemUTC());
    }

    LiveFreshdeskClient(
            RestClient.Builder builder,
            FreshdeskProperties properties,
            TechConnectProperties techConnect,
            Clock clock) {
        if (techConnect.simulationEnabled()) {
            // Both would feed the same queue, and made-up incidents would sit among real ones.
            throw new IllegalStateException(
                    "The simulated and the live Freshdesk are both configured; turn simulation off "
                            + "(TECHCONNECT_SIMULATION_ENABLED=false) to connect the real one.");
        }
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException("techconnect.freshdesk.domain is set but the API key is not.");
        }
        String host = properties.domain().trim().replaceFirst("^https?://", "").replaceAll("/+$", "");
        this.http = builder
                .baseUrl("https://" + host)
                .defaultHeaders(h -> h.setBasicAuth(properties.apiKey(), "X"))
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .requestInterceptor(GET_ONLY)
                .build();
        this.properties = properties;
        this.clock = clock;
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(10));
        factory.setReadTimeout(java.time.Duration.ofSeconds(30));
        return factory;
    }

    @Override
    public List<FreshdeskTicket> ticketsUpdatedSince(Instant since) {
        Instant floor = clock.instant().minus(properties.initialLookback());
        Instant from = since.isBefore(floor) ? floor : since;
        // Freshdesk takes whole seconds; the exact cutoff is applied to what comes back.
        String updatedSince = from.truncatedTo(ChronoUnit.SECONDS).toString();

        List<JsonNode> raw = new ArrayList<>();
        for (int page = 1; page <= Math.max(1, properties.maxPagesPerSync()); page++) {
            Map<String, Object> query = new LinkedHashMap<>();
            query.put("updated_since", updatedSince);
            query.put("order_by", "updated_at");
            query.put("order_type", "asc");
            query.put("include", "requester,description");
            query.put("per_page", PAGE_SIZE);
            query.put("page", page);
            Response response = get("/api/v2/tickets", query);
            response.body().forEach(raw::add);

            String link = response.headers().getFirst(HttpHeaders.LINK);
            if (link == null || !link.contains("rel=\"next\"") || response.budgetLow()) {
                break;
            }
        }

        List<FreshdeskTicket> tickets = new ArrayList<>();
        for (JsonNode node : raw) {
            // Dropping what is older than the exact cursor keeps a repeat pass from re-reading the
            // last ticket of the previous one (the request itself is rounded down to the second).
            Instant updatedAt = instant(node, "updated_at");
            if (updatedAt == null || !updatedAt.isBefore(since)) {
                tickets.add(toTicket(node));
            }
        }
        return tickets;
    }

    private FreshdeskTicket toTicket(JsonNode t) {
        JsonNode requester = t.path("requester");
        Map<String, String> customFields = new HashMap<>();
        t.path("custom_fields").properties().forEach(e -> {
            if (!e.getValue().isNull()) {
                customFields.put(e.getKey(), e.getValue().asString());
            }
        });
        return new FreshdeskTicket(
                text(t, "id"),
                text(t, "subject"),
                text(t, "description_text"),
                text(t, "type"),
                t.path("priority").asInt(2),
                t.path("status").asInt(FreshdeskTicket.STATUS_OPEN),
                companyName(t.path("company_id")),
                text(requester, "name"),
                Optional.ofNullable(text(requester, "phone")).orElse(text(requester, "mobile")),
                instant(t, "due_by"),
                instant(t, "created_at"),
                instant(t, "updated_at"),
                customFields);
    }

    private String companyName(JsonNode id) {
        if (id.isNull() || id.isMissingNode()) {
            return null;
        }
        return companies.computeIfAbsent(id.asLong(), key -> {
            Response response = get("/api/v2/companies/" + key, Map.of());
            return Optional.ofNullable(text(response.body(), "name"));
        }).orElse(null);
    }

    /** What one GET returned. */
    private record Response(JsonNode body, HttpHeaders headers) {

        /** True once less than half the minute's shared request budget remains. */
        boolean budgetLow() {
            String total = headers.getFirst("X-RateLimit-Total");
            String remaining = headers.getFirst("X-RateLimit-Remaining");
            try {
                return total != null && remaining != null
                        && Integer.parseInt(remaining) < Integer.parseInt(total) * RESERVE_FRACTION;
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }

    private Response get(String path, Map<String, ?> query) {
        return http.get()
                .uri(uri -> {
                    uri.path(path);
                    query.forEach(uri::queryParam);
                    return uri.build();
                })
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    if (status == 404) {
                        return new Response(JSON.missingNode(), response.getHeaders());
                    }
                    if (status == 429) {
                        throw new IllegalStateException("Freshdesk rate limit reached; retry after "
                                + response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER) + "s");
                    }
                    if (status == 401 || status == 403) {
                        throw new IllegalStateException("Freshdesk refused the API key (" + status + ")");
                    }
                    if (status >= 300) {
                        throw new IllegalStateException("Freshdesk answered " + status + " for " + path);
                    }
                    return new Response(read(response.getBody().readAllBytes()), response.getHeaders());
                });
    }

    private static JsonNode read(byte[] body) throws IOException {
        return body.length == 0 ? JSON.missingNode() : JSON.readTree(new String(body, StandardCharsets.UTF_8));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNull() || value.isMissingNode() ? null : value.asString();
    }

    private static Instant instant(JsonNode node, String field) {
        String value = text(node, field);
        return value == null ? null : Instant.parse(value);
    }
}
