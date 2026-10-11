package silver.solutions.techconnect.integration.freshdesk;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import silver.solutions.techconnect.entity.Incident;
import silver.solutions.techconnect.entity.IncidentPriority;

class FreshdeskIncidentMapperTest {

    private static final Instant CREATED = Instant.parse("2026-10-11T08:00:00Z");

    private FreshdeskTicket ticket(int priority, Duration sla, Map<String, String> fields) {
        return new FreshdeskTicket(
                "SIM-1", "Switch down", "The core switch is offline.", "Networking", priority,
                FreshdeskTicket.STATUS_OPEN, "Apex Financial", "Lerato Mokoena", "082 555 0142",
                CREATED.plus(sla), CREATED, CREATED, fields);
    }

    private Incident apply(FreshdeskTicket ticket) {
        Incident incident = new Incident();
        FreshdeskIncidentMapper.apply(ticket, incident);
        return incident;
    }

    @Test
    void mapsFreshdeskPrioritiesOntoTheDashboardsThree() {
        assertThat(FreshdeskIncidentMapper.priority(1)).isEqualTo(IncidentPriority.LOW);
        assertThat(FreshdeskIncidentMapper.priority(2)).isEqualTo(IncidentPriority.MEDIUM);
        assertThat(FreshdeskIncidentMapper.priority(3)).isEqualTo(IncidentPriority.HIGH);
        assertThat(FreshdeskIncidentMapper.priority(4)).isEqualTo(IncidentPriority.HIGH);
        assertThat(FreshdeskIncidentMapper.priority(99)).isEqualTo(IncidentPriority.MEDIUM);
    }

    @Test
    void carriesTheTicketsDetailsAcross() {
        Incident incident = apply(ticket(3, Duration.ofHours(4), Map.of(
                FreshdeskIncidentMapper.CF_SITE_ADDRESS, "83 Rivonia Rd, Sandton",
                FreshdeskIncidentMapper.CF_SITE_LATITUDE, "-26.1076",
                FreshdeskIncidentMapper.CF_SITE_LONGITUDE, "28.0567")));

        assertThat(incident.getFreshdeskTicketId()).isEqualTo("SIM-1");
        assertThat(incident.getClientName()).isEqualTo("Apex Financial");
        assertThat(incident.getContactName()).isEqualTo("Lerato Mokoena");
        assertThat(incident.getContactPhone()).isEqualTo("082 555 0142");
        assertThat(incident.getIssueType()).isEqualTo("Networking");
        assertThat(incident.getDescription()).isEqualTo("The core switch is offline.");
        assertThat(incident.getPriority()).isEqualTo(IncidentPriority.HIGH);
        assertThat(incident.getSla()).isEqualTo("4 hrs");
        assertThat(incident.getSlaDueAt()).isEqualTo(CREATED.plus(Duration.ofHours(4)));
        assertThat(incident.getSiteAddress()).isEqualTo("83 Rivonia Rd, Sandton");
        assertThat(incident.getSiteLatitude()).isEqualByComparingTo(new BigDecimal("-26.1076"));
        assertThat(incident.getSiteLongitude()).isEqualByComparingTo(new BigDecimal("28.0567"));
    }

    @Test
    void fallsBackToTheRequesterWhenThereIsNoCompany() {
        FreshdeskTicket t = new FreshdeskTicket(
                "7", "Printer jam", null, null, 2, FreshdeskTicket.STATUS_OPEN, null,
                "Walk-in Wendy", null, null, CREATED, CREATED, null);

        Incident incident = apply(t);

        assertThat(incident.getClientName()).isEqualTo("Walk-in Wendy");
        // No type: the subject stands in for it, and doubles as the description.
        assertThat(incident.getIssueType()).isEqualTo("Printer jam");
        assertThat(incident.getDescription()).isEqualTo("Printer jam");
        assertThat(incident.getSlaDueAt()).isNull();
        assertThat(incident.getSla()).isNull();
    }

    @Test
    void neverLeavesTheClientNameEmpty() {
        FreshdeskTicket t = new FreshdeskTicket(
                "8", null, null, null, 2, FreshdeskTicket.STATUS_OPEN, "  ", null, null, null,
                CREATED, CREATED, null);

        assertThat(apply(t).getClientName()).isEqualTo("Unknown client");
    }

    @Test
    void dropsCoordinatesThatAreIncompleteOrImpossible() {
        Map<String, String> lonely = new HashMap<>();
        lonely.put(FreshdeskIncidentMapper.CF_SITE_LATITUDE, "-26.1");
        assertThat(apply(ticket(2, Duration.ofHours(8), lonely)).getSiteLatitude()).isNull();

        Map<String, String> offPlanet = Map.of(
                FreshdeskIncidentMapper.CF_SITE_LATITUDE, "126.1",
                FreshdeskIncidentMapper.CF_SITE_LONGITUDE, "28.0");
        Incident incident = apply(ticket(2, Duration.ofHours(8), offPlanet));
        assertThat(incident.getSiteLatitude()).isNull();
        assertThat(incident.getSiteLongitude()).isNull();

        Map<String, String> junk = Map.of(
                FreshdeskIncidentMapper.CF_SITE_LATITUDE, "north-ish",
                FreshdeskIncidentMapper.CF_SITE_LONGITUDE, "28.0");
        assertThat(apply(ticket(2, Duration.ofHours(8), junk)).getSiteLatitude()).isNull();
    }

    @Test
    void labelsTheResponseWindowTheWayAPersonWouldSayIt() {
        assertThat(FreshdeskIncidentMapper.slaLabel(ticket(2, Duration.ofMinutes(45), Map.of())))
                .isEqualTo("45 min");
        assertThat(FreshdeskIncidentMapper.slaLabel(ticket(2, Duration.ofHours(1), Map.of())))
                .isEqualTo("1 hr");
        assertThat(FreshdeskIncidentMapper.slaLabel(ticket(2, Duration.ofHours(24), Map.of())))
                .isEqualTo("24 hrs");
    }

    @Test
    void truncatesToTheColumnWidthsInsteadOfFailingTheImport() {
        FreshdeskTicket t = new FreshdeskTicket(
                "9", "x", "x", "T".repeat(500), 2, FreshdeskTicket.STATUS_OPEN, "C".repeat(500),
                "R".repeat(500), "9".repeat(100), CREATED.plusSeconds(3600), CREATED, CREATED,
                Map.of());

        Incident incident = apply(t);

        assertThat(incident.getClientName()).hasSize(255);
        assertThat(incident.getContactName()).hasSize(255);
        assertThat(incident.getContactPhone()).hasSize(32);
        assertThat(incident.getIssueType()).hasSize(128);
    }
}
