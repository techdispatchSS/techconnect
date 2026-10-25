package silver.solutions.techconnect.integration.freshdesk;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;
import silver.solutions.techconnect.entity.FreshdeskUpdate;
import silver.solutions.techconnect.entity.FreshdeskUpdate.Delivery;
import silver.solutions.techconnect.repository.FreshdeskUpdateRepository;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Delivers recorded {@link FreshdeskUpdate}s to Freshdesk. Exists only in {@code LIVE} push mode
 * with a live connection; in every other mode nothing in the application can write to Freshdesk.
 *
 * <p>Even then it can do exactly two things, enforced by {@link #ALLOWED} before a request
 * leaves: add a private note to a ticket, and update a ticket (only ever to resolve it). Updates
 * to one ticket go in order, so if one cannot be delivered yet, the later ones for that ticket
 * wait rather than overtake it (a resolve never lands before its closing note).
 */
@Component
@ConditionalOnExpression("!'${techconnect.freshdesk.domain:}'.isBlank()"
        + " and '${techconnect.freshdesk.push.mode:DEMO}'.equalsIgnoreCase('LIVE')")
public class FreshdeskUpdateSender {

    private static final Logger log = LoggerFactory.getLogger(FreshdeskUpdateSender.class);

    private static final Pattern NOTE_PATH = Pattern.compile("^/api/v2/tickets/\\d+/notes$");
    private static final Pattern TICKET_PATH = Pattern.compile("^/api/v2/tickets/\\d+$");

    /** Lets through a POST of a note or a PUT of a ticket, and nothing else. */
    static final ClientHttpRequestInterceptor ALLOWED = (request, body, execution) -> {
        String path = request.getURI().getPath();
        boolean allowed = (request.getMethod() == HttpMethod.POST && NOTE_PATH.matcher(path).matches())
                || (request.getMethod() == HttpMethod.PUT && TICKET_PATH.matcher(path).matches());
        if (!allowed) {
            throw new IllegalStateException("Freshdesk writes are limited to ticket notes and resolving; refused "
                    + request.getMethod() + " " + path);
        }
        return execution.execute(request, body);
    };

    static final int BATCH = 20;
    static final int MAX_ATTEMPTS = 5;
    private static final int STATUS_RESOLVED = 4;
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private enum Outcome { SENT, REFUSED, RETRY, RATE_LIMITED }

    private record Result(Outcome outcome, String error) {}

    private final RestClient http;
    private final FreshdeskUpdateRepository updates;

    @Autowired
    public FreshdeskUpdateSender(FreshdeskProperties properties, FreshdeskUpdateRepository updates) {
        this(RestClient.builder().requestFactory(timeouts()), properties, updates);
    }

    FreshdeskUpdateSender(
            RestClient.Builder builder, FreshdeskProperties properties, FreshdeskUpdateRepository updates) {
        String host = properties.domain().trim().replaceFirst("^https?://", "").replaceAll("/+$", "");
        this.http = builder
                .baseUrl("https://" + host)
                .defaultHeaders(h -> h.setBasicAuth(properties.apiKey(), "X"))
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .requestInterceptor(ALLOWED)
                .build();
        this.updates = updates;
        log.warn("Freshdesk updates are LIVE: notes and resolves will be written to {}", host);
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(10));
        factory.setReadTimeout(java.time.Duration.ofSeconds(30));
        return factory;
    }

    /** One pass over what is waiting, oldest first. Returns how many were delivered. */
    @Scheduled(
            fixedDelayString = "${techconnect.freshdesk.push.interval:PT15S}",
            initialDelayString = "PT20S")
    public int sendPending() {
        List<FreshdeskUpdate> batch = updates.findByDeliveryOrderByIdAsc(Delivery.PENDING, Limit.of(BATCH));
        Set<String> held = new HashSet<>();
        int sent = 0;
        for (FreshdeskUpdate update : batch) {
            if (held.contains(update.getTicketId())) {
                continue;
            }
            Result result = send(update);
            update.setAttempts(update.getAttempts() + 1);
            switch (result.outcome()) {
                case SENT -> {
                    update.setDelivery(Delivery.SENT);
                    update.setSentAt(Instant.now());
                    update.setLastError(null);
                    sent++;
                }
                case REFUSED -> {
                    update.setDelivery(Delivery.FAILED);
                    update.setLastError(result.error());
                    log.warn("Freshdesk refused update {} for ticket {}: {}",
                            update.getId(), update.getTicketId(), result.error());
                }
                case RETRY, RATE_LIMITED -> {
                    update.setLastError(result.error());
                    if (update.getAttempts() >= MAX_ATTEMPTS) {
                        update.setDelivery(Delivery.FAILED);
                    }
                    held.add(update.getTicketId());
                }
            }
            updates.save(update);
            if (result.outcome() == Outcome.RATE_LIMITED) {
                // The account's limit is shared with everything else using Freshdesk: back off.
                break;
            }
        }
        return sent;
    }

    private Result send(FreshdeskUpdate update) {
        try {
            return switch (update.getAction()) {
                case NOTE -> exchange(HttpMethod.POST, "/api/v2/tickets/" + update.getTicketId() + "/notes",
                        Map.of("body", html(update.getBody()), "private", true));
                case RESOLVE -> exchange(HttpMethod.PUT, "/api/v2/tickets/" + update.getTicketId(),
                        Map.of("status", STATUS_RESOLVED));
            };
        } catch (ResourceAccessException e) {
            return new Result(Outcome.RETRY, "Freshdesk could not be reached");
        }
    }

    private Result exchange(HttpMethod method, String path, Map<String, Object> body) {
        return http.method(method)
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(JSON.writeValueAsString(body))
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    if (status < 300) {
                        return new Result(Outcome.SENT, null);
                    }
                    String detail = "Freshdesk answered " + status;
                    if (status == 429) {
                        return new Result(Outcome.RATE_LIMITED, detail);
                    }
                    if (status >= 500) {
                        return new Result(Outcome.RETRY, detail);
                    }
                    String text = new String(response.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    return new Result(Outcome.REFUSED, text.isBlank() ? detail
                            : detail + ": " + text.substring(0, Math.min(text.length(), 500)));
                });
    }

    /** Freshdesk notes are HTML; ours are plain text. */
    static String html(String text) {
        return text == null ? "" : HtmlUtils.htmlEscape(text).replace("\n", "<br>");
    }
}
