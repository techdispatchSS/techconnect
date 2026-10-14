package silver.solutions.techconnect.dto.response.controller;

/** How many jobs are in each stage, for the sidebar and the tab headings. */
public record JobCountsResponse(long active, long review, long closed) {
}
