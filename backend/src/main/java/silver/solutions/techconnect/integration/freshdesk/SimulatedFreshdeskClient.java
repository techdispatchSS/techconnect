package silver.solutions.techconnect.integration.freshdesk;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * A stand-in Freshdesk that lives in memory, for demos and user testing while the real account
 * is unconfirmed. Tickets only appear when someone raises one ({@link #raise}); they then reach
 * the dashboard through the same polling sync a live Freshdesk would, so what the Controller
 * sees is what the real integration will produce.
 *
 * <p>Ticket ids are prefixed {@code SIM-} so a simulated incident can never be mistaken for a
 * real one in the queue, and cannot collide with a real Freshdesk id later.
 */
@Component
@ConditionalOnProperty(name = "techconnect.simulation.enabled", havingValue = "true")
public class SimulatedFreshdeskClient implements FreshdeskClient {

    /** A believable fault at a believable South African site. */
    private record Scenario(
            String company,
            String requester,
            String phone,
            String site,
            double latitude,
            double longitude,
            String type,
            String subject,
            String description,
            int priority) {}

    private static final List<Scenario> SCENARIOS = List.of(
            new Scenario("Apex Financial Services", "Lerato Mokoena", "082 555 0142",
                    "Sandton City Offices, 83 Rivonia Rd, Sandton", -26.1076, 28.0567,
                    "Networking", "Core switch offline - whole floor without network",
                    "Fourth-floor core switch has dropped off the network. About 60 staff cannot reach "
                            + "the file server or internet. Cabinet is in the comms room behind reception.",
                    3),
            new Scenario("Riverside Mall Management", "Pieter van der Merwe", "083 555 0178",
                    "Riverside Mall, Bridge St, Pretoria East", -25.7870, 28.3270,
                    "CCTV", "Parking-level cameras not recording",
                    "Basement parking cameras show 'no signal' on the control-room wall. "
                            + "Recorder is powered and reachable but reports disk errors.",
                    2),
            new Scenario("Umhlanga Medical Centre", "Dr Naidoo", "031 555 0119",
                    "Umhlanga Medical Centre, 12 Ridge Rd, Umhlanga", -29.7260, 31.0840,
                    "Power / UPS", "UPS alarm - server room running on battery",
                    "Mains power returned an hour ago but the UPS is still on battery and alarming. "
                            + "Estimated 25 minutes of runtime left for the patient-records servers.",
                    4),
            new Scenario("Cape Quarter Retail", "Aisha Davids", "021 555 0163",
                    "Cape Quarter, 27 Somerset Rd, De Waterkant, Cape Town", -33.9172, 18.4158,
                    "Wi-Fi", "Guest Wi-Fi dropping every few minutes",
                    "Tenants report the guest network disconnecting repeatedly, mostly on the "
                            + "upper courtyard. Staff network seems unaffected.",
                    1),
            new Scenario("Midrand Logistics Hub", "Sipho Dlamini", "011 555 0187",
                    "Midrand Logistics Hub, Old Pretoria Rd, Midrand", -25.9975, 28.1273,
                    "Access control", "Boom gate and turnstiles not reading cards",
                    "Shift change starts in two hours and none of the staff cards are being "
                            + "accepted at the main gate or the warehouse turnstiles.",
                    3),
            new Scenario("Bryanston Law Chambers", "Thandi Khumalo", "011 555 0123",
                    "Bryanston Law Chambers, Wierda Rd, Bryanston", -26.0560, 28.0200,
                    "Fibre", "Fibre link down since 06:00",
                    "Primary fibre link is down and the failover LTE is saturated. Court filings "
                            + "are due today. ISP says their side is clear.",
                    3),
            new Scenario("Centurion Data Services", "Johan Botha", "012 555 0191",
                    "Centurion Data Services, Lenchen Ave, Centurion", -25.8600, 28.1900,
                    "Server room cooling", "Server room temperature climbing",
                    "Room temperature has risen from 21C to 29C over the morning. One of the two "
                            + "cooling units is silent. Non-essential servers have been shut down.",
                    3));

    private final List<FreshdeskTicket> tickets = new CopyOnWriteArrayList<>();
    private final AtomicInteger next = new AtomicInteger(ThreadLocalRandom.current().nextInt(SCENARIOS.size()));

    @Override
    public List<FreshdeskTicket> ticketsUpdatedSince(Instant since) {
        return tickets.stream()
                .filter(t -> !t.updatedAt().isBefore(since))
                .sorted(Comparator.comparing(FreshdeskTicket::updatedAt))
                .toList();
    }

    /**
     * Edits a ticket as an agent in Freshdesk would (change its priority, resolve it, ...). The
     * ticket's {@code updatedAt} moves forward, which is what makes the next sync see it.
     */
    public void amend(String id, UnaryOperator<FreshdeskTicket> change) {
        for (int i = 0; i < tickets.size(); i++) {
            FreshdeskTicket current = tickets.get(i);
            if (current.id().equals(id)) {
                FreshdeskTicket edited = change.apply(current);
                Instant touched = Instant.now();
                if (!touched.isAfter(current.updatedAt())) {
                    touched = current.updatedAt().plusMillis(1);
                }
                tickets.set(i, new FreshdeskTicket(
                        edited.id(), edited.subject(), edited.descriptionText(), edited.type(),
                        edited.priority(), edited.status(), edited.companyName(),
                        edited.requesterName(), edited.requesterPhone(), edited.dueBy(),
                        edited.createdAt(), touched, edited.customFields()));
                return;
            }
        }
        throw new IllegalArgumentException("No simulated ticket " + id);
    }

    /**
     * Raises a new ticket in the simulated Freshdesk. With {@code overdue} the ticket is already
     * past its resolution due date, so the Overdue view and SLA styling can be demonstrated.
     */
    public FreshdeskTicket raise(boolean overdue) {
        Scenario s = SCENARIOS.get(Math.floorMod(next.getAndIncrement(), SCENARIOS.size()));
        Instant now = Instant.now();

        Duration sla = switch (s.priority()) {
            case 4 -> Duration.ofHours(2);
            case 3 -> Duration.ofHours(4);
            case 2 -> Duration.ofHours(8);
            default -> Duration.ofHours(24);
        };
        // An overdue ticket was raised long enough ago that its SLA lapsed half an hour back.
        Instant createdAt = overdue ? now.minus(sla).minus(Duration.ofMinutes(30)) : now;

        FreshdeskTicket ticket = new FreshdeskTicket(
                "SIM-" + Long.toString(System.currentTimeMillis(), 36).toUpperCase()
                        + Integer.toString(tickets.size() % 36, 36).toUpperCase(),
                s.subject(),
                s.description(),
                s.type(),
                s.priority(),
                FreshdeskTicket.STATUS_OPEN,
                s.company(),
                s.requester(),
                s.phone(),
                createdAt.plus(sla),
                createdAt,
                now,
                Map.of(
                        FreshdeskIncidentMapper.CF_SITE_ADDRESS, s.site(),
                        FreshdeskIncidentMapper.CF_SITE_LATITUDE, Double.toString(s.latitude()),
                        FreshdeskIncidentMapper.CF_SITE_LONGITUDE, Double.toString(s.longitude())));
        tickets.add(ticket);
        return ticket;
    }
}
