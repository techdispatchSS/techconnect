package silver.solutions.techconnect.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Works out which address a request really came from, for rate limiting.
 *
 * <p>{@code X-Forwarded-For} is attacker-controlled up to the first proxy we run: a client can
 * send {@code X-Forwarded-For: 1.2.3.4} themselves and a proxy will simply append their real
 * address after it. Trusting the leftmost entry would let anyone pick a fresh rate-limit key per
 * request. Entries are therefore counted in from the RIGHT, by the number of proxies we operate
 * (each of which appended exactly one entry), and only that entry is believed.
 */
public final class ClientIp {

    /** Anything that is not a plausible IPv4/IPv6 literal is ignored, so a header can never
     * inject log lines or create unbounded distinct rate-limit keys out of arbitrary text. */
    private static final Pattern IP_LITERAL = Pattern.compile("[0-9a-fA-F:.]{3,45}");

    private ClientIp() {}

    public static String resolve(HttpServletRequest request, int trustedProxyHops) {
        String peer = request.getRemoteAddr();
        if (trustedProxyHops <= 0) {
            return peer;
        }

        List<String> chain = new ArrayList<>();
        Enumeration<String> headers = request.getHeaders("X-Forwarded-For");
        while (headers != null && headers.hasMoreElements()) {
            for (String part : headers.nextElement().split(",")) {
                chain.add(part.trim());
            }
        }

        int index = chain.size() - trustedProxyHops;
        if (index < 0) {
            // Fewer entries than proxies we expected: the header was stripped or never added,
            // so the socket peer is the best address we have.
            return peer;
        }

        String candidate = chain.get(index);
        return IP_LITERAL.matcher(candidate).matches() ? candidate : peer;
    }
}
