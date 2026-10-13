package silver.solutions.techconnect.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import silver.solutions.techconnect.AbstractPostgresIntegrationTest;
import silver.solutions.techconnect.service.DocumentStorage;

/**
 * A failure nobody planned for must reach the client as what it is (a 500), not as a 401.
 *
 * <p>When an exception escapes a controller the servlet container forwards to {@code /error} to
 * render it, and Spring Security applies to that second dispatch as well. With every other path
 * requiring a sign-in, the error page itself was "unauthorised", so an anonymous applicant saw
 * 401 for what was really a storage failure. That is invisible to MockMvc (it never makes the
 * error dispatch), so this runs against a real server on a random port.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "techconnect.applications.per-ip-per-hour=100000",
    "techconnect.applications.global-per-hour=1000000"
})
class UnhandledFailureStatusIntegrationTest extends AbstractPostgresIntegrationTest {

    /** Storage that fails the way an unplanned bug would: not with an ApiException. */
    @TestConfiguration
    static class ExplodingStorage {
        @Bean
        @Primary
        DocumentStorage explodingStorage() {
            return new DocumentStorage() {
                @Override
                public void put(String key, byte[] content, String contentType) {
                    throw new IllegalStateException("simulated failure no handler knows about");
                }

                @Override
                public byte[] get(String key) {
                    throw new IllegalStateException("simulated");
                }

                @Override
                public void delete(String key) {
                    // nothing was stored
                }
            };
        }
    }

    @Value("${local.server.port}")
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private static void part(ByteArrayOutputStream out, String boundary, String disposition,
            String contentType, byte[] body) throws Exception {
        out.write(("--" + boundary + "\r\nContent-Disposition: " + disposition
                + "\r\nContent-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> submitValidApplication() throws Exception {
        String unique = UUID.randomUUID().toString();
        long n = Math.abs(UUID.randomUUID().getMostSignificantBits() % 9_000_000L) + 1_000_000L;
        String json = """
                {"firstName":"Nomsa","lastName":"Khumalo","email":"boom-%s@techconnect.test",
                 "phone":"+27 82 %d %04d","idNumber":"%s",
                 "address":{"street":"1 Test Street","suburb":"Umhlanga","city":"Durban",
                            "province":"KWAZULU_NATAL","postalCode":"4001"},
                 "experienceSummary":"Eight years installing and maintaining network systems.",
                 "skills":null,
                 "documents":[{"kind":"ID_DOCUMENT","title":"ID document"},
                              {"kind":"MATRIC_CERTIFICATE","title":"Matric certificate"},
                              {"kind":"PROOF_OF_RESIDENCE","title":"Proof of residence"}],
                 "consent":true,"website":""}"""
                .formatted(unique, n / 10_000, n % 10_000, SaIds.random());

        String boundary = "----tc" + UUID.randomUUID();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        part(out, boundary, "form-data; name=\"application\"; filename=\"blob\"",
                "application/json", json.getBytes(StandardCharsets.UTF_8));
        for (String name : new String[] {"id.pdf", "matric.pdf", "residence.pdf"}) {
            part(out, boundary, "form-data; name=\"files\"; filename=\"" + name + "\"",
                    "application/pdf", TestFiles.pdf());
        }
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/v1/applications"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void anUnplannedFailureStoringADocumentIsA500NotA401() throws Exception {
        HttpResponse<String> response = submitValidApplication();

        assertThat(response.statusCode())
                .as("an anonymous applicant must not be told they are unauthorised: %s",
                        response.body())
                .isEqualTo(500);
    }

    @Test
    void aProtectedRouteStillAsksForASignIn() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/v1/admin/applications"))
                .GET()
                .build();

        assertThat(http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode())
                .isEqualTo(401);
    }
}
