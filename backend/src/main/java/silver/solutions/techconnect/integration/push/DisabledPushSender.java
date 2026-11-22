package silver.solutions.techconnect.integration.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/** Firebase isn't configured: nothing is sent, and email still goes out as usual. */
@Component
@ConditionalOnExpression(FcmPushSender.NOT_CONFIGURED)
class DisabledPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(DisabledPushSender.class);

    @Override
    public Result send(String token, PushMessage message) {
        log.debug("Push is not configured; not sending \"{}\"", message.title());
        return Result.FAILED;
    }
}
