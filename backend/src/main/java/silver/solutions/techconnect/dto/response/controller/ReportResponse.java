package silver.solutions.techconnect.dto.response.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import silver.solutions.techconnect.entity.IncidentPriority;

/**
 * The controller's performance report for a date range. Every figure is computed from what the
 * system recorded; a measure with nothing to measure is {@code null}, never zero, so a quiet
 * week does not read as "0% compliance".
 */
public record ReportResponse(
        LocalDate from,
        LocalDate to,
        String zone,
        Incidents incidents,
        Sla sla,
        Dispatching dispatching,
        Jobs jobs,
        List<PriorityRow> byPriority,
        List<TechnicianRow> byTechnician,
        List<DayRow> daily) {

    /** {@code open} and {@code overdueNow} are as of now, not as of the end of the range. */
    public record Incidents(long created, long closed, long open, long overdueNow) {
    }

    /** Of the incidents closed in the range that had an SLA. */
    public record Sla(long withinSla, long late, Double compliancePercent) {
    }

    public record Dispatching(
            long dispatches,
            long accepted,
            long expired,
            long invitations,
            long accepts,
            long declines,
            long noResponses,
            Double averageMinutesToAccept,
            Double acceptRatePercent) {
    }

    public record Jobs(
            long opened,
            long submitted,
            long closed,
            Double averageHoursOnSite,
            Double averageLabourHours,
            Double averageHoursToClose) {
    }

    public record PriorityRow(
            IncidentPriority priority, long created, long closed, long withinSla, long late) {
    }

    public record TechnicianRow(
            UUID technicianId,
            String name,
            long invited,
            long accepted,
            long declined,
            long noResponse,
            long jobs,
            long jobsClosed,
            Double averageLabourHours) {
    }

    public record DayRow(LocalDate date, long created, long closed) {
    }
}
