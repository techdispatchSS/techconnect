package silver.solutions.techconnect.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import silver.solutions.techconnect.dto.response.controller.ReportResponse;
import silver.solutions.techconnect.entity.Dispatch;
import silver.solutions.techconnect.entity.DispatchResponse;
import silver.solutions.techconnect.entity.DispatchResponseStatus;
import silver.solutions.techconnect.entity.DispatchStatus;
import silver.solutions.techconnect.entity.Incident;
import silver.solutions.techconnect.entity.IncidentPriority;
import silver.solutions.techconnect.entity.IncidentStatus;
import silver.solutions.techconnect.entity.Job;
import silver.solutions.techconnect.entity.JobStatus;
import silver.solutions.techconnect.entity.User;
import silver.solutions.techconnect.exception.ApiException;
import silver.solutions.techconnect.mapper.IncidentMapper;
import silver.solutions.techconnect.repository.DispatchRepository;
import silver.solutions.techconnect.repository.DispatchResponseRepository;
import silver.solutions.techconnect.repository.IncidentRepository;
import silver.solutions.techconnect.repository.JobRepository;
import silver.solutions.techconnect.repository.UserRepository;

/**
 * The controller's performance report: how quickly work was taken, whether the SLA was met, and
 * what each technician did, for a range of days.
 *
 * <p>Days are the controller's own calendar days in {@code zone}, so "yesterday" is yesterday for
 * the person reading it. The figures are computed from the rows in the range rather than by
 * database aggregates: the volumes here are a controller's day-to-day work, and the same rows
 * feed the CSV, so the page and the export can never disagree.
 */
@Service
public class ReportService {

    /** A year at most; a wider range is a data export, not a report. */
    private static final int MAX_DAYS = 366;
    private static final int DEFAULT_DAYS = 30;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final IncidentRepository incidents;
    private final DispatchRepository dispatches;
    private final DispatchResponseRepository responses;
    private final JobRepository jobs;
    private final UserRepository users;

    public ReportService(
            IncidentRepository incidents,
            DispatchRepository dispatches,
            DispatchResponseRepository responses,
            JobRepository jobs,
            UserRepository users) {
        this.incidents = incidents;
        this.dispatches = dispatches;
        this.responses = responses;
        this.jobs = jobs;
        this.users = users;
    }

    /** A resolved range: the days asked for, and the instants that bound them. */
    public record Range(LocalDate from, LocalDate to, ZoneId zone) {

        Instant start() {
            return from.atStartOfDay(zone).toInstant();
        }

        /** Exclusive. */
        Instant end() {
            return to.plusDays(1).atStartOfDay(zone).toInstant();
        }
    }

    /** Fills in the defaults (the last 30 days) and refuses a range that makes no sense. */
    public static Range range(LocalDate from, LocalDate to, String zoneId) {
        ZoneId zone;
        try {
            zone = zoneId == null || zoneId.isBlank() ? ZoneId.of("UTC") : ZoneId.of(zoneId.trim());
        } catch (RuntimeException e) {
            throw ApiException.badRequest("That is not a time zone.");
        }
        LocalDate end = to != null ? to : LocalDate.now(zone);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS - 1L);
        if (start.isAfter(end)) {
            throw ApiException.badRequest("The start date is after the end date.");
        }
        if (Duration.between(start.atStartOfDay(), end.plusDays(1).atStartOfDay()).toDays() > MAX_DAYS) {
            throw ApiException.badRequest("Pick a range of a year or less.");
        }
        return new Range(start, end, zone);
    }

    @Transactional(readOnly = true)
    public ReportResponse summary(Range range) {
        Instant start = range.start();
        Instant end = range.end();

        List<Incident> created = incidents.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(start, end);
        List<Incident> closed = incidents.findByClosedAtGreaterThanEqualAndClosedAtLessThan(start, end);
        List<Dispatch> sent = dispatches.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(start, end);
        List<Job> opened = jobs.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(start, end);

        Map<UUID, List<DispatchResponse>> responsesByDispatch = sent.isEmpty()
                ? Map.of()
                : responses.findByDispatchIdIn(sent.stream().map(Dispatch::getId).toList()).stream()
                        .collect(Collectors.groupingBy(DispatchResponse::getDispatchId));

        long open = incidents.countByStatusNot(IncidentStatus.CLOSED);
        long overdueNow = incidents.countOverdue(IncidentStatus.CLOSED, IncidentStatus.OVERDUE, Instant.now());

        return new ReportResponse(
                range.from(),
                range.to(),
                range.zone().getId(),
                new ReportResponse.Incidents(created.size(), closed.size(), open, overdueNow),
                sla(closed),
                dispatching(sent, responsesByDispatch),
                jobsSection(opened),
                byPriority(created, closed),
                byTechnician(sent, responsesByDispatch, opened),
                daily(range, created, closed));
    }

    // --- sections -----------------------------------------------------------------------------

    private static ReportResponse.Sla sla(List<Incident> closed) {
        long within = closed.stream().filter(i -> metSla(i) == Boolean.TRUE).count();
        long late = closed.stream().filter(i -> metSla(i) == Boolean.FALSE).count();
        return new ReportResponse.Sla(within, late, percent(within, within + late));
    }

    /** True or false once closed with an SLA; null when there was no SLA to meet. */
    private static Boolean metSla(Incident incident) {
        if (incident.getSlaDueAt() == null || incident.getClosedAt() == null) {
            return null;
        }
        return !incident.getClosedAt().isAfter(incident.getSlaDueAt());
    }

    private static ReportResponse.Dispatching dispatching(
            List<Dispatch> sent, Map<UUID, List<DispatchResponse>> byDispatch) {
        long accepted = sent.stream().filter(d -> d.getStatus() == DispatchStatus.ACCEPTED).count();
        long expired = sent.stream().filter(d -> d.getStatus() == DispatchStatus.EXPIRED).count();

        List<DispatchResponse> all = byDispatch.values().stream().flatMap(List::stream).toList();
        long accepts = count(all, DispatchResponseStatus.ACCEPTED);
        long declines = count(all, DispatchResponseStatus.DECLINED);
        long none = count(all, DispatchResponseStatus.NO_RESPONSE);

        Map<UUID, Dispatch> dispatchById = sent.stream().collect(Collectors.toMap(Dispatch::getId, d -> d));
        OptionalDouble minutes = all.stream()
                .filter(r -> r.getResponse() == DispatchResponseStatus.ACCEPTED && r.getRespondedAt() != null)
                .mapToDouble(r -> Duration.between(
                        dispatchById.get(r.getDispatchId()).getCreatedAt(), r.getRespondedAt()).toSeconds() / 60.0)
                .average();

        return new ReportResponse.Dispatching(
                sent.size(), accepted, expired, all.size(), accepts, declines, none,
                minutes.isPresent() ? round(minutes.getAsDouble()) : null,
                // Of the dispatches that have been settled; one still waiting is neither.
                percent(accepted, accepted + expired));
    }

    private static ReportResponse.Jobs jobsSection(List<Job> opened) {
        long submitted = opened.stream().filter(j -> j.getSubmittedAt() != null).count();
        long closed = opened.stream().filter(j -> j.getStatus() == JobStatus.CLOSED).count();

        OptionalDouble onSite = opened.stream()
                .filter(j -> j.getArrivedAt() != null && j.getCompletedAt() != null)
                .mapToDouble(j -> Duration.between(j.getArrivedAt(), j.getCompletedAt()).toMinutes() / 60.0)
                .average();
        OptionalDouble labour = opened.stream()
                .filter(j -> j.getLabourHours() != null)
                .mapToDouble(j -> j.getLabourHours().doubleValue())
                .average();
        OptionalDouble toClose = opened.stream()
                .filter(j -> j.getClosedAt() != null)
                .mapToDouble(j -> Duration.between(j.getCreatedAt(), j.getClosedAt()).toMinutes() / 60.0)
                .average();

        return new ReportResponse.Jobs(
                opened.size(), submitted, closed,
                onSite.isPresent() ? round(onSite.getAsDouble()) : null,
                labour.isPresent() ? round(labour.getAsDouble()) : null,
                toClose.isPresent() ? round(toClose.getAsDouble()) : null);
    }

    private static List<ReportResponse.PriorityRow> byPriority(List<Incident> created, List<Incident> closed) {
        Map<IncidentPriority, long[]> rows = new EnumMap<>(IncidentPriority.class);
        for (IncidentPriority p : IncidentPriority.values()) {
            rows.put(p, new long[4]);
        }
        for (Incident i : created) {
            if (i.getPriority() != null) {
                rows.get(i.getPriority())[0]++;
            }
        }
        for (Incident i : closed) {
            if (i.getPriority() == null) {
                continue;
            }
            long[] row = rows.get(i.getPriority());
            row[1]++;
            Boolean met = metSla(i);
            if (met == Boolean.TRUE) {
                row[2]++;
            } else if (met == Boolean.FALSE) {
                row[3]++;
            }
        }
        List<ReportResponse.PriorityRow> out = new ArrayList<>();
        // Most urgent first, whatever the enum's declaration order happens to be.
        for (IncidentPriority p : List.of(IncidentPriority.HIGH, IncidentPriority.MEDIUM, IncidentPriority.LOW)) {
            long[] r = rows.get(p);
            out.add(new ReportResponse.PriorityRow(p, r[0], r[1], r[2], r[3]));
        }
        return out;
    }

    private List<ReportResponse.TechnicianRow> byTechnician(
            List<Dispatch> sent, Map<UUID, List<DispatchResponse>> byDispatch, List<Job> opened) {

        Map<UUID, List<DispatchResponse>> answers = byDispatch.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.groupingBy(DispatchResponse::getTechnicianId));
        Map<UUID, List<Job>> jobsBy = opened.stream().collect(Collectors.groupingBy(Job::getTechnicianId));

        Set<UUID> ids = new HashSet<>(answers.keySet());
        ids.addAll(jobsBy.keySet());
        Map<UUID, String> names = ids.isEmpty()
                ? Map.of()
                : users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, User::getName));

        return ids.stream()
                .map(id -> {
                    List<DispatchResponse> mine = answers.getOrDefault(id, List.of());
                    List<Job> myJobs = jobsBy.getOrDefault(id, List.of());
                    OptionalDouble labour = myJobs.stream()
                            .filter(j -> j.getLabourHours() != null)
                            .mapToDouble(j -> j.getLabourHours().doubleValue())
                            .average();
                    return new ReportResponse.TechnicianRow(
                            id,
                            names.getOrDefault(id, "Unknown"),
                            mine.size(),
                            count(mine, DispatchResponseStatus.ACCEPTED),
                            count(mine, DispatchResponseStatus.DECLINED),
                            count(mine, DispatchResponseStatus.NO_RESPONSE),
                            myJobs.size(),
                            myJobs.stream().filter(j -> j.getStatus() == JobStatus.CLOSED).count(),
                            labour.isPresent() ? round(labour.getAsDouble()) : null);
                })
                .sorted(Comparator.comparingLong(ReportResponse.TechnicianRow::jobs).reversed()
                        .thenComparing(ReportResponse.TechnicianRow::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static List<ReportResponse.DayRow> daily(Range range, List<Incident> created, List<Incident> closed) {
        Map<LocalDate, long[]> days = new TreeMap<>();
        for (LocalDate d = range.from(); !d.isAfter(range.to()); d = d.plusDays(1)) {
            days.put(d, new long[2]);
        }
        for (Incident i : created) {
            long[] day = days.get(i.getCreatedAt().atZone(range.zone()).toLocalDate());
            if (day != null) {
                day[0]++;
            }
        }
        for (Incident i : closed) {
            long[] day = days.get(i.getClosedAt().atZone(range.zone()).toLocalDate());
            if (day != null) {
                day[1]++;
            }
        }
        return days.entrySet().stream()
                .map(e -> new ReportResponse.DayRow(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .toList();
    }

    // --- CSV ----------------------------------------------------------------------------------

    /**
     * One row per incident logged or closed in the range, for a spreadsheet. Anything a spreadsheet
     * would run as a formula is neutralised: a client name is typed by outsiders, and "=HYPERLINK(...)"
     * must open as text.
     */
    @Transactional(readOnly = true)
    public byte[] incidentsCsv(Range range) {
        Instant start = range.start();
        Instant end = range.end();

        Map<UUID, Incident> rows = new LinkedHashMap<>();
        incidents.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(start, end)
                .forEach(i -> rows.put(i.getId(), i));
        incidents.findByClosedAtGreaterThanEqualAndClosedAtLessThan(start, end)
                .forEach(i -> rows.putIfAbsent(i.getId(), i));

        Map<UUID, List<Job>> jobsByIncident = rows.isEmpty()
                ? Map.of()
                : jobs.findByIncidentIdIn(rows.keySet()).stream()
                        .collect(Collectors.groupingBy(Job::getIncidentId));
        Map<UUID, String> names = users
                .findAllById(jobsByIncident.values().stream().flatMap(List::stream)
                        .map(Job::getTechnicianId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, User::getName));

        StringBuilder csv = new StringBuilder();
        csv.append(row("Ticket", "Client", "Site", "Priority", "Status", "Logged", "SLA due", "Closed",
                "Met SLA", "Technician", "Job status", "Labour hours"));

        rows.values().stream()
                .sorted(Comparator.comparing(Incident::getCreatedAt))
                .forEach(i -> {
                    Job job = jobsByIncident.getOrDefault(i.getId(), List.of()).stream()
                            .max(Comparator.comparing(Job::getCreatedAt)).orElse(null);
                    Boolean met = metSla(i);
                    csv.append(row(
                            IncidentMapper.ticketRef(i),
                            i.getClientName(),
                            i.getSiteAddress(),
                            i.getPriority() == null ? "" : i.getPriority().name(),
                            i.getStatus().name(),
                            stamp(i.getCreatedAt(), range.zone()),
                            stamp(i.getSlaDueAt(), range.zone()),
                            stamp(i.getClosedAt(), range.zone()),
                            met == null ? "" : met ? "Yes" : "No",
                            job == null ? "" : names.getOrDefault(job.getTechnicianId(), ""),
                            job == null ? "" : job.getStatus().name(),
                            job == null || job.getLabourHours() == null
                                    ? "" : job.getLabourHours().toPlainString()));
                });

        // A byte-order mark so Excel reads the UTF-8 (names with accents) correctly.
        return ("﻿" + csv).getBytes(StandardCharsets.UTF_8);
    }

    private static String stamp(Instant instant, ZoneId zone) {
        return instant == null ? "" : STAMP.format(instant.atZone(zone));
    }

    private static String row(String... cells) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                line.append(',');
            }
            line.append(cell(cells[i]));
        }
        return line.append("\r\n").toString();
    }

    /** Quotes a cell, and defuses one that a spreadsheet would treat as a formula. */
    static String cell(String raw) {
        String value = raw == null ? "" : raw;
        if (!value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0) {
            value = "'" + value;
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    // --- small helpers ------------------------------------------------------------------------

    private static long count(Collection<DispatchResponse> all, DispatchResponseStatus status) {
        return all.stream().filter(r -> r.getResponse() == status).count();
    }

    private static Double percent(long part, long whole) {
        return whole == 0 ? null : round(part * 100.0 / whole);
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
