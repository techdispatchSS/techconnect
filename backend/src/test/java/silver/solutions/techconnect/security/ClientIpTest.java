package silver.solutions.techconnect.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpTest {

    private MockHttpServletRequest request(String peer, String... forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(peer);
        for (String value : forwardedFor) {
            request.addHeader("X-Forwarded-For", value);
        }
        return request;
    }

    @Test
    void usesTheSocketPeerWhenNoProxyIsTrusted() {
        // Even with a forged header present: with no proxy of ours in front, it's just a client.
        assertThat(ClientIp.resolve(request("9.9.9.9", "1.2.3.4"), 0)).isEqualTo("9.9.9.9");
    }

    @Test
    void believesOnlyTheEntryOurOwnProxyAppended() {
        // The client sent "6.6.6.6" itself; our proxy then appended the real address 5.5.5.5.
        assertThat(ClientIp.resolve(request("10.0.0.1", "6.6.6.6, 5.5.5.5"), 1))
                .as("a forged leftmost entry must not become the rate-limit key")
                .isEqualTo("5.5.5.5");
    }

    @Test
    void countsInFromTheRightAcrossSeveralProxies() {
        assertThat(ClientIp.resolve(request("10.0.0.1", "6.6.6.6", "5.5.5.5, 10.0.0.2"), 2))
                .isEqualTo("5.5.5.5");
    }

    @Test
    void fallsBackToThePeerWhenTheHeaderIsMissingOrShort() {
        assertThat(ClientIp.resolve(request("10.0.0.1"), 1)).isEqualTo("10.0.0.1");
        assertThat(ClientIp.resolve(request("10.0.0.1", "5.5.5.5"), 2)).isEqualTo("10.0.0.1");
    }

    @Test
    void ignoresAHeaderValueThatIsNotAnIpAddress() {
        assertThat(ClientIp.resolve(request("10.0.0.1", "<script>alert(1)</script>"), 1))
                .isEqualTo("10.0.0.1");
        assertThat(ClientIp.resolve(request("10.0.0.1", "5.5.5.5\r\nInjected: yes"), 1))
                .isEqualTo("10.0.0.1");
    }
}
