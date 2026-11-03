package silver.solutions.techconnect.integration.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** The request shape FCM's HTTP v1 API expects, and how its answers are read. */
class FcmPushSenderTest {

    private static final PushSender.PushMessage MESSAGE =
            new PushSender.PushMessage("New job offer", "Apex · Sandton", "/jobs");
    private static final String SEND_URL = "https://fcm.googleapis.com/v1/projects/tc-test/messages:send";

    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://fcm.googleapis.com");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final FcmPushSender sender = new FcmPushSender(builder, () -> "the-access-token", "tc-test");

    @Test
    void sendsADataMessageThatIsUrgentAndLapsesWithTheOffer() {
        server.expect(requestTo(SEND_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer the-access-token"))
                .andExpect(jsonPath("$.message.token").value("device-1"))
                .andExpect(jsonPath("$.message.data.title").value("New job offer"))
                .andExpect(jsonPath("$.message.data.body").value("Apex · Sandton"))
                .andExpect(jsonPath("$.message.data.url").value("/jobs"))
                .andExpect(jsonPath("$.message.webpush.headers.Urgency").value("high"))
                .andExpect(jsonPath("$.message.webpush.headers.TTL").value("600"))
                .andRespond(withSuccess("{\"name\":\"projects/tc-test/messages/1\"}", MediaType.APPLICATION_JSON));

        assertThat(sender.send("device-1", MESSAGE)).isEqualTo(PushSender.Result.SENT);
        server.verify();
    }

    @Test
    void aTokenFcmNoLongerKnowsIsDead() {
        server.expect(requestTo(SEND_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"status\":\"NOT_FOUND\",\"details\":[{\"errorCode\":\"UNREGISTERED\"}]}}"));

        assertThat(sender.send("gone", MESSAGE)).isEqualTo(PushSender.Result.DEAD_TOKEN);
    }

    @Test
    void otherFailuresKeepTheTokenAndNeverThrow() {
        server.expect(requestTo(SEND_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(sender.send("device-1", MESSAGE)).isEqualTo(PushSender.Result.FAILED);

        server.reset();
        server.expect(requestTo(SEND_URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));
        assertThat(sender.send("device-1", MESSAGE)).isEqualTo(PushSender.Result.FAILED);
    }
}
