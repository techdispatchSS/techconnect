package silver.solutions.techconnect.dto.response.controller;

public record IncidentKpiResponse(
        long newCount,
        long unassignedCount,
        long inProgressCount,
        long overdueCount) {
}
