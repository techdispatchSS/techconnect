package silver.solutions.techconnect.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import silver.solutions.techconnect.dto.response.common.RealtimeEvent;
import silver.solutions.techconnect.entity.IncidentPriority;
import silver.solutions.techconnect.entity.IncidentStatus;
import silver.solutions.techconnect.entity.TechnicianStatus;
import silver.solutions.techconnect.entity.User;
import silver.solutions.techconnect.repository.JobRepository;

/** Live updates over a real socket (#16): who gets told, who doesn't, and how quickly. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RealtimePushIntegrationTest extends AbstractDispatchIntegrationTest {

    private static final String CONTROLLER_TOPIC = "/topic/controller/events";
    private static final String OWN_EVENTS = "/user/queue/events";
    /** The §9.1 target: on the dashboard within 3 seconds of the action. */
    private static final long WITHIN_SECONDS = 3;

    @LocalServerPort private int port;
    @Autowired private SimpUserRegistry socketUsers;
    @Autowired private JobRepository jobs;

    private final List<StompSession> sessions = new ArrayList<>();

    @AfterEach
    void disconnect() {
        sessions.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
    }

    @Test
    void controllersAndTheTechnicianHearAboutADispatchAndItsJobWithinTheTarget() throws Exception {
        String controller = controllerToken();
        User technician = technician("pushed", TechnicianStatus.AVAILABLE);
        String technicianToken = tokenFor(technician);
        LinkedBlockingQueue<RealtimeEvent> toController = subscribe(controller, CONTROLLER_TOPIC);
        LinkedBlockingQueue<RealtimeEvent> toTechnician = subscribe(technicianToken, OWN_EVENTS);

        var incident = incident("PushCo", IncidentStatus.NEW, IncidentPriority.HIGH);
        UUID dispatchId = dispatch(controller, incident, "ASSIGN", technician);

        RealtimeEvent offered = next(toTechnician, RealtimeEvent.Type.DISPATCH_CHANGED);
        assertThat(offered.dispatchId()).isEqualTo(dispatchId);
        assertThat(offered.incidentId()).isEqualTo(incident.getId());
        assertThat(offered.status()).isEqualTo("PENDING");
        assertThat(next(toController, RealtimeEvent.Type.DISPATCH_CHANGED).dispatchId())
                .isEqualTo(dispatchId);

        mvc.perform(post("/v1/technician/dispatches/{id}/accept", dispatchId)
                        .header("Authorization", "Bearer " + technicianToken))
                .andExpect(status().isOk());
        RealtimeEvent opened = next(toController, RealtimeEvent.Type.JOB_CHANGED);
        assertThat(opened.status()).isEqualTo("EN_ROUTE");
        UUID jobId = jobs.findByDispatchId(dispatchId).orElseThrow().getId();
        assertThat(opened.jobId()).isEqualTo(jobId);
        assertThat(next(toController, RealtimeEvent.Type.DISPATCH_CHANGED).status()).isEqualTo("ACCEPTED");

        mvc.perform(put("/v1/technician/jobs/{id}/status", jobId)
                        .header("Authorization", "Bearer " + technicianToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ON_SITE\"}"))
                .andExpect(status().isOk());
        assertThat(next(toController, RealtimeEvent.Type.JOB_CHANGED).status()).isEqualTo("ON_SITE");
        assertThat(next(toTechnician, RealtimeEvent.Type.JOB_CHANGED).status()).isIn("EN_ROUTE", "ON_SITE");
    }

    @Test
    void aTechnicianHearsNothingAboutOtherPeoplesWork() throws Exception {
        String controller = controllerToken();
        User invited = technician("invited", TechnicianStatus.AVAILABLE);
        User other = technician("bystander", TechnicianStatus.AVAILABLE);
        LinkedBlockingQueue<RealtimeEvent> toInvited = subscribe(tokenFor(invited), OWN_EVENTS);
        LinkedBlockingQueue<RealtimeEvent> toOther = subscribe(tokenFor(other), OWN_EVENTS);

        dispatch(controller, incident("MineCo", IncidentStatus.NEW, IncidentPriority.LOW), "ASSIGN", invited);

        assertThat(next(toInvited, RealtimeEvent.Type.DISPATCH_CHANGED)).isNotNull();
        assertThat(toOther.poll(500, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void aTechnicianCannotListenToTheControllersTopic() throws Exception {
        String controller = controllerToken();
        User technician = technician("eavesdrop", TechnicianStatus.AVAILABLE);
        StompSession session = connect(tokenFor(technician));
        LinkedBlockingQueue<RealtimeEvent> heard = new LinkedBlockingQueue<>();
        session.subscribe(CONTROLLER_TOPIC, collectInto(heard));

        // The server answers a forbidden subscribe by closing the session.
        await().atMost(Duration.ofSeconds(WITHIN_SECONDS)).until(() -> !session.isConnected());

        dispatch(controller, incident("TopicCo", IncidentStatus.NEW, IncidentPriority.LOW), "ASSIGN", technician);
        assertThat(heard.poll(500, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void aSocketNeedsAValidToken() {
        assertThatThrownBy(() -> connect(null)).isInstanceOf(ExecutionException.class);
        assertThatThrownBy(() -> connect("not-a-token")).isInstanceOf(ExecutionException.class);
    }

    private LinkedBlockingQueue<RealtimeEvent> subscribe(String token, String destination) throws Exception {
        LinkedBlockingQueue<RealtimeEvent> received = new LinkedBlockingQueue<>();
        StompSession session = connect(token);
        int before = subscribersOf(destination);
        session.subscribe(destination, collectInto(received));
        // The broker doesn't acknowledge a subscribe, so wait until it is registered.
        await().atMost(Duration.ofSeconds(WITHIN_SECONDS))
                .until(() -> subscribersOf(destination) > before);
        return received;
    }

    private int subscribersOf(String destination) {
        return socketUsers.findSubscriptions(s -> destination.equals(s.getDestination())).size();
    }

    private StompSession connect(String token) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());
        StompHeaders connect = new StompHeaders();
        if (token != null) {
            connect.add("Authorization", "Bearer " + token);
        }
        StompSession session = client
                .connectAsync("ws://localhost:" + port + "/api/ws", new WebSocketHttpHeaders(), connect,
                        new StompSessionHandlerAdapter() { })
                .get(WITHIN_SECONDS, TimeUnit.SECONDS);
        sessions.add(session);
        return session;
    }

    private static StompFrameHandler collectInto(LinkedBlockingQueue<RealtimeEvent> queue) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return RealtimeEvent.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.add((RealtimeEvent) payload);
            }
        };
    }

    /** The next event of this type, within the target. Events of other types may come first. */
    private static RealtimeEvent next(LinkedBlockingQueue<RealtimeEvent> queue, RealtimeEvent.Type type)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WITHIN_SECONDS);
        while (true) {
            RealtimeEvent event = queue.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
            assertThat(event).as("a %s event within %ds", type, WITHIN_SECONDS).isNotNull();
            if (event.type() == type) {
                return event;
            }
        }
    }
}
