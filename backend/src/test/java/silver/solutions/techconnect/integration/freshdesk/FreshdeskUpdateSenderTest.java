package silver.solutions.techconnect.integration.freshdesk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import silver.solutions.techconnect.entity.FreshdeskUpdate;
import silver.solutions.techconnect.entity.FreshdeskUpdate.Action;
import silver.solutions.techconnect.entity.FreshdeskUpdate.Delivery;
import silver.solutions.techconnect.entity.FreshdeskUpdate.Event;
import silver.solutions.techconnect.repository.FreshdeskUpdateRepository;

class FreshdeskUpdateSenderTest {

    private static final String BASE = "https://example.freshdesk.com";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final FreshdeskUpdateRepository repository = mock(FreshdeskUpdateRepository.class);
    private final List<FreshdeskUpdate> pending = new ArrayList<>();
    private long nextId = 1;

    private FreshdeskUpdateSender sender() {
        when(repository.findByDeliveryOrderByIdAsc(eq(Delivery.PENDING), any(Limit.class))).thenReturn(pending);
        return new FreshdeskUpdateSender(builder,
                new FreshdeskProperties("example.freshdesk.com", "secret-key", Duration.ofDays(1), 5, null),
                repository);
    }

    private FreshdeskUpdate queue(String ticket, Action action, String body) {
        FreshdeskUpdate update = new FreshdeskUpdate();
        update.setId(nextId++);
        update.setIncidentId(UUID.randomUUID());
        update.setTicketId(ticket);
        update.setEvent(action == Action.RESOLVE ? Event.JOB_CLOSED : Event.JOB_PROGRESS);
        update.setAction(action);
        update.setBody(body);
        update.setDelivery(Delivery.PENDING);
        pending.add(update);
        return update;
    }

    @Test
    void sendsNotesAsPrivateHtmlAndResolvesTheTicket() {
        FreshdeskUpdate note = queue("201", Action.NOTE, "Closed by Thabo: <done>\nAll good");
        FreshdeskUpdate resolve = queue("201", Action.RESOLVE, null);
        server.expect(once(), requestTo(BASE + "/api/v2/tickets/201/notes"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(
                        "{\"body\":\"Closed by Thabo: &lt;done&gt;<br>All good\",\"private\":true}", true))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(BASE + "/api/v2/tickets/201"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().json("{\"status\":4}", true))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(sender().sendPending()).isEqualTo(2);
        server.verify();

        assertThat(note.getDelivery()).isEqualTo(Delivery.SENT);
        assertThat(note.getSentAt()).isNotNull();
        assertThat(resolve.getDelivery()).isEqualTo(Delivery.SENT);
    }

    @Test
    void aRefusedUpdateFailsForGoodWithFreshdesksReason() {
        FreshdeskUpdate note = queue("404", Action.NOTE, "hello");
        server.expect(once(), requestTo(BASE + "/api/v2/tickets/404/notes"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .body("{\"code\":\"access_denied\"}").contentType(MediaType.APPLICATION_JSON));

        assertThat(sender().sendPending()).isZero();
        server.verify();
        assertThat(note.getDelivery()).isEqualTo(Delivery.FAILED);
        assertThat(note.getLastError()).contains("404").contains("access_denied");
    }

    @Test
    void anOutageHoldsBackLaterUpdatesForThatTicketOnly() {
        FreshdeskUpdate note = queue("201", Action.NOTE, "closing note");
        FreshdeskUpdate resolve = queue("201", Action.RESOLVE, null);
        FreshdeskUpdate other = queue("202", Action.NOTE, "elsewhere");
        server.expect(once(), requestTo(BASE + "/api/v2/tickets/201/notes"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        // No request for 201's resolve: it must not land before its note.
        server.expect(once(), requestTo(BASE + "/api/v2/tickets/202/notes"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(sender().sendPending()).isEqualTo(1);
        server.verify();
        assertThat(note.getDelivery()).isEqualTo(Delivery.PENDING);
        assertThat(note.getAttempts()).isEqualTo(1);
        assertThat(resolve.getDelivery()).isEqualTo(Delivery.PENDING);
        assertThat(resolve.getAttempts()).isZero();
        assertThat(other.getDelivery()).isEqualTo(Delivery.SENT);
    }

    @Test
    void givesUpAfterRepeatedFailures() {
        FreshdeskUpdate note = queue("201", Action.NOTE, "closing note");
        note.setAttempts(FreshdeskUpdateSender.MAX_ATTEMPTS - 1);
        server.expect(once(), requestTo(BASE + "/api/v2/tickets/201/notes"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        sender().sendPending();
        assertThat(note.getDelivery()).isEqualTo(Delivery.FAILED);
    }

    @Test
    void backsOffForTheRestOfThePassWhenRateLimited() {
        FreshdeskUpdate first = queue("201", Action.NOTE, "one");
        FreshdeskUpdate second = queue("202", Action.NOTE, "two");
        server.expect(once(), requestTo(BASE + "/api/v2/tickets/201/notes"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThat(sender().sendPending()).isZero();
        server.verify();
        assertThat(first.getDelivery()).isEqualTo(Delivery.PENDING);
        assertThat(second.getAttempts()).isZero();
    }

    @Test
    void canOnlyAddNotesAndUpdateTickets() {
        RestClient.Builder raw = RestClient.builder().requestInterceptor(FreshdeskUpdateSender.ALLOWED);
        MockRestServiceServer rawServer = MockRestServiceServer.bindTo(raw).build();
        RestClient http = raw.build();

        record Attempt(HttpMethod method, String path) {}
        for (Attempt attempt : List.of(
                new Attempt(HttpMethod.DELETE, "/api/v2/tickets/201"),
                new Attempt(HttpMethod.GET, "/api/v2/tickets/201"),
                new Attempt(HttpMethod.POST, "/api/v2/tickets"),
                new Attempt(HttpMethod.POST, "/api/v2/tickets/201/reply"),
                new Attempt(HttpMethod.PUT, "/api/v2/contacts/5"),
                new Attempt(HttpMethod.PUT, "/api/v2/tickets/SIM-1"))) {
            assertThatThrownBy(() -> http.method(attempt.method()).uri(BASE + attempt.path())
                            .retrieve().toBodilessEntity())
                    .as(attempt.toString())
                    .hasStackTraceContaining("limited to ticket notes and resolving");
        }
        rawServer.verify();
    }
}
