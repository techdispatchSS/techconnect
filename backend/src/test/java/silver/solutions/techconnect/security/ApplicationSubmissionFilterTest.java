package silver.solutions.techconnect.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.unit.DataSize;
import silver.solutions.techconnect.config.TechConnectProperties;

class ApplicationSubmissionFilterTest {

    private ApplicationSubmissionFilter filter(int perIp, int hops) {
        var props = new TechConnectProperties(
                null, null, null, null, null, null, null,
                new TechConnectProperties.Security(hops),
                new TechConnectProperties.Applications(
                        10, DataSize.ofMegabytes(10), DataSize.ofMegabytes(32), perIp, 1000));
        return new ApplicationSubmissionFilter(
                new SubmissionThrottle(perIp, 1000, Duration.ofHours(1), Clock.systemUTC()), props);
    }

    /** A request that declares {@code contentLength} (or none, if negative) without allocating it. */
    private MockHttpServletRequest post(String path, long contentLength) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path) {
            @Override
            public long getContentLengthLong() {
                return contentLength;
            }
        };
        request.setServletPath(path);
        request.setRemoteAddr("7.7.7.7");
        request.setContentType("multipart/form-data; boundary=x");
        return request;
    }

    private MockHttpServletResponse run(ApplicationSubmissionFilter filter, MockHttpServletRequest r)
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(r, response, new MockFilterChain());
        return response;
    }

    @Test
    void letsAnInRangeSubmissionThrough() throws Exception {
        assertThat(run(filter(5, 0), post("/v1/applications", 1000)).getStatus()).isEqualTo(200);
    }

    @Test
    void refusesADeclaredBodyBiggerThanTheCeiling() throws Exception {
        var response = run(filter(5, 0), post("/v1/applications", 40L * 1024 * 1024));
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("too large");
    }

    @Test
    void refusesABodyThatDoesNotDeclareItsSize() throws Exception {
        // A chunked, unbounded stream can't be size-checked up front, so it isn't accepted.
        assertThat(run(filter(5, 0), post("/v1/applications", -1)).getStatus()).isEqualTo(411);
    }

    @Test
    void throttlesAnAddressWith429AndARetryAfter() throws Exception {
        var filter = filter(2, 0);
        assertThat(run(filter, post("/v1/applications", 10)).getStatus()).isEqualTo(200);
        assertThat(run(filter, post("/v1/applications", 10)).getStatus()).isEqualTo(200);

        var blocked = run(filter, post("/v1/applications", 10));
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isNotNull();
    }

    @Test
    void aForgedForwardedForHeaderCannotDodgeTheThrottle() throws Exception {
        var filter = filter(1, 1);

        var first = post("/v1/applications", 10);
        first.addHeader("X-Forwarded-For", "1.1.1.1, 5.5.5.5");
        assertThat(run(filter, first).getStatus()).isEqualTo(200);

        // Same real client (5.5.5.5, appended by our proxy), new forged leftmost value.
        var second = post("/v1/applications", 10);
        second.addHeader("X-Forwarded-For", "2.2.2.2, 5.5.5.5");
        assertThat(run(filter, second).getStatus()).isEqualTo(429);
    }

    @Test
    void ignoresEveryOtherRoute() throws Exception {
        var filter = filter(1, 0);
        for (int i = 0; i < 5; i++) {
            assertThat(run(filter, post("/v1/auth/login", 10)).getStatus()).isEqualTo(200);
        }
    }
}
