package silver.solutions.techconnect.integration.freshdesk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import silver.solutions.techconnect.config.TechConnectProperties;

/** Against payloads shaped exactly like the owners' account returns (see scripts/freshdesk). */
class LiveFreshdeskClientTest {

    private static final String BASE = "https://example.freshdesk.com";
    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");
    private static final Instant MIDNIGHT = Instant.parse("2026-10-20T00:00:00Z");
    private static final String NEXT_PAGE = "<" + BASE + "/api/v2/tickets?page=2>; rel=\"next\"";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private LiveFreshdeskClient client(int maxPages, boolean simulation) {
        return new LiveFreshdeskClient(
                builder,
                new FreshdeskProperties("https://example.freshdesk.com/", "secret-key", Duration.ofDays(1), maxPages, null),
                new TechConnectProperties(null, null, null, null, null, null, null, null, null,
                        new TechConnectProperties.Simulation(simulation)),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private LiveFreshdeskClient client(int maxPages) {
        return client(maxPages, false);
    }

    private static String fixture(String name) throws IOException {
        return new ClassPathResource("freshdesk/" + name + ".json").getContentAsString(StandardCharsets.UTF_8);
    }

    private static HttpHeaders budget(int remaining, String link) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RateLimit-Total", "100");
        headers.set("X-RateLimit-Remaining", Integer.toString(remaining));
        if (link != null) {
            headers.set(HttpHeaders.LINK, link);
        }
        return headers;
    }

    private void expectPage(int page, String fixture, HttpHeaders headers) throws IOException {
        server.expect(once(), requestTo(Matchers.startsWith(BASE + "/api/v2/tickets?")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("page", Integer.toString(page)))
                .andRespond(withSuccess(fixture(fixture), MediaType.APPLICATION_JSON).headers(headers));
    }

    private void expectCompany() throws IOException {
        server.expect(once(), requestTo(BASE + "/api/v2/companies/68000000900"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(fixture("company"), MediaType.APPLICATION_JSON));
    }

    @Test
    void readsEveryPageAndMapsTheRealPayloadShape() throws IOException {
        String auth = "Basic " + Base64.getEncoder().encodeToString("secret-key:X".getBytes(StandardCharsets.UTF_8));
        server.expect(once(), requestTo(Matchers.startsWith(BASE + "/api/v2/tickets?")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(queryParam("updated_since", "2026-10-20T00:00:00Z"))
                .andExpect(queryParam("order_by", "updated_at"))
                .andExpect(queryParam("order_type", "asc"))
                .andExpect(queryParam("include", "requester,description"))
                .andExpect(queryParam("per_page", "100"))
                .andExpect(queryParam("page", "1"))
                .andRespond(withSuccess(fixture("tickets-page1"), MediaType.APPLICATION_JSON)
                        .headers(budget(98, NEXT_PAGE)));
        expectPage(2, "tickets-page2", budget(96, null));
        expectCompany();

        List<FreshdeskTicket> tickets = client(5).ticketsUpdatedSince(Instant.parse("2026-10-20T00:00:00Z"));
        server.verify();

        assertThat(tickets).extracting(FreshdeskTicket::id).containsExactly("201", "202", "203");

        FreshdeskTicket incident = tickets.get(0);
        assertThat(incident.subject()).isEqualTo("FSE Request - Example Retail Sandton - till offline");
        assertThat(incident.descriptionText()).isEqualTo("Till 3 will not power on.");
        assertThat(incident.type()).isEqualTo("Incident");
        assertThat(incident.priority()).isEqualTo(3);
        assertThat(incident.status()).isEqualTo(FreshdeskTicket.STATUS_OPEN);
        assertThat(incident.companyName()).isEqualTo("Example Retail");
        assertThat(incident.requesterName()).isEqualTo("Thabo Nkosi");
        // No phone on the contact, so the mobile stands in.
        assertThat(incident.requesterPhone()).isEqualTo("082 555 0101");
        assertThat(incident.dueBy()).isEqualTo(Instant.parse("2026-10-20T11:00:00Z"));
        assertThat(incident.createdAt()).isEqualTo(Instant.parse("2026-10-20T07:00:00Z"));
        assertThat(incident.updatedAt()).isEqualTo(Instant.parse("2026-10-20T07:05:00Z"));
        assertThat(incident.customFields()).containsEntry("cf_reference_number", "4471");

        FreshdeskTicket alert = tickets.get(1);
        assertThat(alert.type()).isNull();
        assertThat(alert.companyName()).isNull();
        assertThat(alert.requesterPhone()).isNull();
        assertThat(alert.customFields()).isEmpty();
        // The account's own "Waiting on Customer" status is still open work.
        assertThat(alert.status()).isEqualTo(6);
        assertThat(alert.isResolved()).isFalse();

        // Same company again: answered from the cache, not a second request.
        assertThat(tickets.get(2).companyName()).isEqualTo("Example Retail");
        assertThat(tickets.get(2).isResolved()).isTrue();
    }

    @Test
    void reachesNoFurtherBackThanTheInitialLookback() throws IOException {
        server.expect(once(), requestTo(Matchers.startsWith(BASE + "/api/v2/tickets?")))
                .andExpect(queryParam("updated_since", "2026-10-19T12:00:00Z"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client(5).ticketsUpdatedSince(Instant.EPOCH)).isEmpty();
        server.verify();
    }

    @Test
    void leavesOutTheTicketThePreviousPassAlreadyEndedOn() throws IOException {
        // The sync's cursor sits 1 ms past ticket 201; Freshdesk only takes whole seconds.
        expectPage(1, "tickets-page1", budget(98, null));

        List<FreshdeskTicket> tickets = client(5).ticketsUpdatedSince(Instant.parse("2026-10-20T07:05:00.001Z"));
        server.verify();

        // 201 is dropped before its company is looked up, so no company request is made either.
        assertThat(tickets).extracting(FreshdeskTicket::id).containsExactly("202");
    }

    @Test
    void readsNoMoreThanTheConfiguredPagesInOnePass() throws IOException {
        expectPage(1, "tickets-page1", budget(98, NEXT_PAGE));
        expectCompany();

        assertThat(client(1).ticketsUpdatedSince(MIDNIGHT)).hasSize(2);
        server.verify();
    }

    @Test
    void stopsPagingOnceHalfTheSharedRateBudgetIsGone() throws IOException {
        expectPage(1, "tickets-page1", budget(40, NEXT_PAGE));
        expectCompany();

        assertThat(client(5).ticketsUpdatedSince(MIDNIGHT)).hasSize(2);
        server.verify();
    }

    @Test
    void failsWithoutRetryingWhenRateLimited() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "42");
        server.expect(once(), requestTo(Matchers.startsWith(BASE + "/api/v2/tickets?")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(headers));

        assertThatThrownBy(() -> client(5).ticketsUpdatedSince(NOW.minusSeconds(3600)))
                .hasMessageContaining("rate limit")
                .hasMessageContaining("42");
        server.verify();
    }

    @Test
    void failsClearlyWhenTheKeyIsRefused() {
        server.expect(once(), requestTo(Matchers.startsWith(BASE + "/api/v2/tickets?")))
                .andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> client(5).ticketsUpdatedSince(NOW.minusSeconds(3600)))
                .hasMessageContaining("refused the API key");
    }

    @Test
    void refusesToSendAnythingButGet() {
        RestClient.Builder raw = RestClient.builder().requestInterceptor(LiveFreshdeskClient.GET_ONLY);
        MockRestServiceServer rawServer = MockRestServiceServer.bindTo(raw).build();
        RestClient http = raw.build();

        for (HttpMethod write : List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)) {
            assertThatThrownBy(() -> http.method(write).uri(BASE + "/api/v2/tickets/201")
                            .retrieve().toBodilessEntity())
                    .hasStackTraceContaining("read-only");
        }
        // Nothing reached the (mock) network.
        rawServer.verify();
    }

    @Test
    void refusesToRunAlongsideTheSimulatedFreshdesk() {
        assertThatThrownBy(() -> client(5, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("simulation off");
    }
}
