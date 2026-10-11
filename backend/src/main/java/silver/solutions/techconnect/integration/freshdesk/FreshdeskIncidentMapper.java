package silver.solutions.techconnect.integration.freshdesk;

import java.math.BigDecimal;
import java.time.Duration;
import silver.solutions.techconnect.entity.Incident;
import silver.solutions.techconnect.entity.IncidentPriority;
import silver.solutions.techconnect.util.Strings;

/**
 * Turns a Freshdesk ticket into the fields of an {@link Incident}.
 *
 * <p>The three {@code cf_*} names are assumptions about how the owners' Freshdesk will carry the
 * site address and its coordinates; they are the one piece that has to be confirmed against the
 * real account.
 */
public final class FreshdeskIncidentMapper {

    public static final String CF_SITE_ADDRESS = "cf_site_address";
    public static final String CF_SITE_LATITUDE = "cf_site_latitude";
    public static final String CF_SITE_LONGITUDE = "cf_site_longitude";

    private FreshdeskIncidentMapper() {}

    /**
     * Copies a ticket's details onto an incident. Deliberately leaves {@code status} alone: that
     * is TechConnect's own workflow (new, being dispatched, ...), and a ticket edit in Freshdesk
     * must not undo it. Closing is the one exception, handled by the sync.
     */
    public static void apply(FreshdeskTicket ticket, Incident incident) {
        incident.setFreshdeskTicketId(limit(ticket.id(), 64));
        incident.setClientName(limit(firstNonBlank(
                ticket.companyName(), ticket.requesterName(), "Unknown client"), 255));
        incident.setContactName(limit(clean(ticket.requesterName()), 255));
        incident.setContactPhone(limit(clean(ticket.requesterPhone()), 32));
        incident.setIssueType(limit(firstNonBlank(ticket.type(), ticket.subject(), null), 128));
        incident.setDescription(clean(firstNonBlank(ticket.descriptionText(), ticket.subject(), null)));
        incident.setPriority(priority(ticket.priority()));

        incident.setSiteAddress(clean(ticket.customField(CF_SITE_ADDRESS)));
        incident.setSiteLatitude(decimal(ticket.customField(CF_SITE_LATITUDE), 90));
        incident.setSiteLongitude(decimal(ticket.customField(CF_SITE_LONGITUDE), 180));
        // A lone coordinate is worse than none: it would plot a pin on the equator.
        if (incident.getSiteLatitude() == null || incident.getSiteLongitude() == null) {
            incident.setSiteLatitude(null);
            incident.setSiteLongitude(null);
        }

        incident.setSlaDueAt(ticket.dueBy());
        incident.setSla(slaLabel(ticket));
    }

    /** Freshdesk's four priorities onto the dashboard's three: urgent is simply high. */
    static IncidentPriority priority(int freshdeskPriority) {
        return switch (freshdeskPriority) {
            case 1 -> IncidentPriority.LOW;
            case 3, 4 -> IncidentPriority.HIGH;
            default -> IncidentPriority.MEDIUM;
        };
    }

    /** The response window as a person would say it: "4 hrs", or "45 min" for a short one. */
    static String slaLabel(FreshdeskTicket ticket) {
        if (ticket.dueBy() == null || ticket.createdAt() == null) {
            return null;
        }
        long minutes = Duration.between(ticket.createdAt(), ticket.dueBy()).toMinutes();
        if (minutes <= 0) {
            return null;
        }
        if (minutes < 60) {
            return minutes + " min";
        }
        long hours = Math.round(minutes / 60.0);
        return hours + (hours == 1 ? " hr" : " hrs");
    }

    private static BigDecimal decimal(String raw, int bound) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(raw.trim());
            return value.abs().compareTo(BigDecimal.valueOf(bound)) <= 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String clean(String value) {
        return Strings.blankToNull(Strings.cleanText(value));
    }

    private static String firstNonBlank(String first, String second, String fallback) {
        String a = clean(first);
        if (a != null) {
            return a;
        }
        String b = clean(second);
        return b != null ? b : fallback;
    }

    private static String limit(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
