package silver.solutions.techconnect.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import silver.solutions.techconnect.dto.response.technician.PushConfigResponse;
import silver.solutions.techconnect.entity.PushDevice;
import silver.solutions.techconnect.entity.PushPlatform;
import silver.solutions.techconnect.entity.User;
import silver.solutions.techconnect.integration.push.PushProperties;
import silver.solutions.techconnect.integration.push.PushSender;
import silver.solutions.techconnect.repository.PushDeviceRepository;

/**
 * Job alerts on a technician's phone (#69), so an offer reaches them with the app closed. An
 * addition to the email, never a replacement: alerts are opt-in, and a device may not have them.
 */
@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);

    /** Where a tapped alert opens: the technician's home, which lists their offers. */
    private static final String OFFERS_URL = "/jobs";

    private final PushDeviceRepository devices;
    private final PushSender sender;
    private final PushProperties properties;

    public PushService(PushDeviceRepository devices, PushSender sender, PushProperties properties) {
        this.devices = devices;
        this.sender = sender;
        this.properties = properties;
    }

    public PushConfigResponse config() {
        return properties.webConfigured()
                ? new PushConfigResponse(
                        true,
                        properties.web().apiKey(),
                        properties.projectId(),
                        properties.web().messagingSenderId(),
                        properties.web().appId(),
                        properties.web().vapidKey())
                : PushConfigResponse.disabled();
    }

    /** Sent each time the app opens, as tokens change. A token follows whoever registered last. */
    @Transactional
    public void register(UUID userId, String token, PushPlatform platform) {
        PushDevice device = devices.findByToken(token).orElseGet(PushDevice::new);
        device.setUserId(userId);
        device.setToken(token);
        device.setPlatform(platform);
        device.setLastSeenAt(Instant.now());
        devices.save(device);
    }

    /** Only removes the caller's own device, so nobody can turn off someone else's alerts. */
    @Transactional
    public void unregister(UUID userId, String token) {
        devices.findByToken(token)
                .filter(device -> device.getUserId().equals(userId))
                .ifPresent(devices::delete);
    }

    /**
     * Tells each technician about an offer, on all their devices. Runs after the dispatch has
     * committed, and never throws: one bad device must not stop the others, or the emails.
     */
    public void offerJob(List<User> technicians, NotificationService.DispatchNotice notice) {
        try {
            Map<UUID, User> active = technicians.stream()
                    .filter(User::isActive)
                    .collect(Collectors.toMap(User::getId, Function.identity()));
            if (active.isEmpty()) {
                return;
            }
            PushSender.PushMessage message = new PushSender.PushMessage(
                    notice.reminder() ? "Reminder: a job is waiting for you" : "New job offer",
                    body(notice),
                    OFFERS_URL);
            for (PushDevice device : devices.findByUserIdIn(active.keySet())) {
                if (sender.send(device.getToken(), message) == PushSender.Result.DEAD_TOKEN) {
                    devices.removeDeadToken(device.getToken());
                }
            }
        } catch (RuntimeException e) {
            log.warn("Could not send job alerts", e);
        }
    }

    private static String body(NotificationService.DispatchNotice notice) {
        return notice.siteAddress() == null
                ? notice.clientName()
                : notice.clientName() + " · " + notice.siteAddress();
    }
}
