package silver.solutions.techconnect.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import silver.solutions.techconnect.config.TechConnectProperties;

/**
 * Gate in front of the one unauthenticated endpoint that accepts file uploads.
 *
 * <p>It is a servlet filter rather than controller code on purpose: multipart bodies are parsed
 * before a controller method is ever invoked, so a limit enforced there would only run after an
 * attacker had already made the server receive and buffer everything. Here the request is
 * refused on its headers alone — rate-limited per client address, and rejected outright if it
 * declares a body bigger than the configured ceiling or doesn't declare a length at all (so a
 * chunked, unbounded stream can't be used to slip past the size check).
 */
@Component
public class ApplicationSubmissionFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApplicationSubmissionFilter.class);

    private static final String SUBMIT_PATH = "/v1/applications";

    private final SubmissionThrottle throttle;
    private final TechConnectProperties properties;

    public ApplicationSubmissionFilter(
            SubmissionThrottle throttle, TechConnectProperties properties) {
        this.throttle = throttle;
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !"POST".equals(request.getMethod()) || !SUBMIT_PATH.equals(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String client = ClientIp.resolve(request, properties.security().trustedProxyHops());

        long retryAfter = throttle.acquire(client);
        if (retryAfter > 0) {
            log.warn("Application submissions throttled for {}", client);
            response.setHeader("Retry-After", Long.toString(retryAfter));
            reject(response, HttpStatus.TOO_MANY_REQUESTS,
                    "Too many applications from your connection. Please try again later.");
            return;
        }

        long declared = request.getContentLengthLong();
        if (declared < 0) {
            reject(response, HttpStatus.LENGTH_REQUIRED, "Content-Length is required.");
            return;
        }
        if (declared > properties.applications().maxRequestSize().toBytes()) {
            reject(response, HttpStatus.CONTENT_TOO_LARGE,
                    "That submission is too large. Please attach smaller files.");
            return;
        }

        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
