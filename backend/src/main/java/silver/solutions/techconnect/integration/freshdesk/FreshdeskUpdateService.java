package silver.solutions.techconnect.integration.freshdesk;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import silver.solutions.techconnect.entity.Dispatch;
import silver.solutions.techconnect.entity.DispatchType;
import silver.solutions.techconnect.entity.FreshdeskUpdate;
import silver.solutions.techconnect.entity.FreshdeskUpdate.Action;
import silver.solutions.techconnect.entity.FreshdeskUpdate.Delivery;
import silver.solutions.techconnect.entity.FreshdeskUpdate.Event;
import silver.solutions.techconnect.entity.Incident;
import silver.solutions.techconnect.entity.Job;
import silver.solutions.techconnect.entity.JobStatus;
import silver.solutions.techconnect.entity.User;
import silver.solutions.techconnect.integration.freshdesk.FreshdeskProperties.PushMode;
import silver.solutions.techconnect.repository.FreshdeskUpdateRepository;
import silver.solutions.techconnect.repository.IncidentRepository;
import silver.solutions.techconnect.repository.UserRepository;

/**
 * Writes down what an incident's Freshdesk ticket should be told as the work moves along, in the
 * same transaction as the change itself. Nothing is sent from here: in live mode
 * {@link FreshdeskUpdateSender} delivers the rows; in demo mode they are only recorded, so the
 * dashboard can show exactly what would go to Freshdesk without anything reaching it.
 *
 * <p>Incidents raised by hand have no ticket, and get no updates. Simulated tickets ({@code SIM-})
 * are always demo, whatever the mode, since they do not exist in any Freshdesk.
 */
@Service
public class FreshdeskUpdateService {

    private static final Logger log = LoggerFactory.getLogger(FreshdeskUpdateService.class);

    private final FreshdeskUpdateRepository updates;
    private final IncidentRepository incidents;
    private final UserRepository users;
    private final FreshdeskProperties properties;
    private final boolean live;

    public FreshdeskUpdateService(
            FreshdeskUpdateRepository updates,
            IncidentRepository incidents,
            UserRepository users,
            FreshdeskProperties properties,
            ObjectProvider<FreshdeskUpdateSender> sender) {
        this.updates = updates;
        this.incidents = incidents;
        this.users = users;
        this.properties = properties;
        // Live only when there is a live connection to send through; asked for LIVE without one,
        // updates are still recorded, as demo, rather than queueing up for a sender that never runs.
        this.live = properties.pushMode() == PushMode.LIVE && sender.getIfAvailable() != null;
        log.info("Freshdesk updates: {}{}", properties.pushMode(),
                properties.pushMode() == PushMode.LIVE && !live ? " requested, no live connection: recording as DEMO" : "");
    }

    /** How updates are handled right now: OFF, DEMO or LIVE. */
    public PushMode effectiveMode() {
        PushMode mode = properties.pushMode();
        return mode == PushMode.LIVE && !live ? PushMode.DEMO : mode;
    }

    public List<FreshdeskUpdate> forIncident(UUID incidentId) {
        return updates.findByIncidentIdOrderByIdAsc(incidentId);
    }

    public void dispatchSent(Incident incident, Dispatch dispatch, List<User> technicians) {
        List<String> names = technicians.stream().map(User::getName).toList();
        String body = dispatch.getDispatchType() == DispatchType.ASSIGN
                ? "TechConnect assigned this job to " + String.join(", ", names) + "."
                : "TechConnect sent this job to " + names.size() + " technician"
                        + (names.size() == 1 ? "" : "s") + " (" + String.join(", ", names)
                        + "). Waiting for one to accept.";
        record(incident, Event.DISPATCH_SENT, Action.NOTE, body);
    }

    public void dispatchExpired(UUID incidentId) {
        incidents.findById(incidentId).ifPresent(incident -> record(incident, Event.DISPATCH_EXPIRED,
                Action.NOTE, "No technician accepted the job in time. It is back in the TechConnect queue."));
    }

    /** Called for every recorded job transition (JobService). */
    public void jobChanged(Job job, JobStatus previous, JobStatus status, String reason, UUID by) {
        Incident incident = incidents.findById(job.getIncidentId()).orElse(null);
        if (incident == null) {
            return;
        }
        String who = users.findById(by).map(User::getName).orElse("The technician");
        switch (status) {
            case EN_ROUTE -> record(incident, Event.DISPATCH_ACCEPTED, Action.NOTE,
                    who + " accepted the job and is on the way to site.");
            case ON_SITE -> record(incident, Event.JOB_PROGRESS, Action.NOTE, who + " arrived on site.");
            case WORK_STARTED -> record(incident, Event.JOB_PROGRESS, Action.NOTE, who + " started work.");
            case ON_HOLD -> record(incident, Event.JOB_PROGRESS, Action.NOTE, "Work on hold: " + reason);
            case WORK_RESUMED -> record(incident, Event.JOB_PROGRESS, Action.NOTE, who + " resumed work.");
            case COMPLETE -> record(incident, Event.JOB_PROGRESS, Action.NOTE,
                    who + " finished the work on site. Report to follow.");
            case SUBMITTED -> record(incident, Event.REPORT_SUBMITTED, Action.NOTE, report(job, who));
            case CLOSED -> {
                record(incident, Event.JOB_CLOSED, Action.NOTE, "Closed in TechConnect by " + who
                        + (reason == null ? "." : ": " + reason));
                record(incident, Event.JOB_CLOSED, Action.RESOLVE, null);
            }
        }
    }

    private static String report(Job job, String who) {
        List<String> lines = new ArrayList<>();
        lines.add("Technician's report from " + who + ":");
        line(lines, "Resolution", job.getResolutionNotes());
        line(lines, "Root cause", job.getRootCause());
        line(lines, "Recommendations", job.getRecommendations());
        BigDecimal hours = job.getLabourHours();
        if (hours != null) {
            lines.add("Labour: " + hours.stripTrailingZeros().toPlainString() + " h");
        }
        return String.join("\n", lines);
    }

    private static void line(List<String> lines, String label, String value) {
        if (value != null) {
            lines.add(label + ": " + value);
        }
    }

    private void record(Incident incident, Event event, Action action, String body) {
        String ticket = incident.getFreshdeskTicketId();
        if (ticket == null || !properties.pushes(event)) {
            return;
        }
        FreshdeskUpdate update = new FreshdeskUpdate();
        update.setIncidentId(incident.getId());
        update.setTicketId(ticket);
        update.setEvent(event);
        update.setAction(action);
        update.setBody(body);
        update.setDelivery(live && !ticket.startsWith("SIM-") ? Delivery.PENDING : Delivery.DEMO);
        updates.save(update);
    }
}
