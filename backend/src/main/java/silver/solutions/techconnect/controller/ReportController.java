package silver.solutions.techconnect.controller;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import silver.solutions.techconnect.dto.response.controller.ReportResponse;
import silver.solutions.techconnect.service.ReportService;

/**
 * The controller's reports. CONTROLLER-only — enforced centrally by the
 * {@code /v1/controller/**} rule in {@code SecurityConfig}.
 *
 * <p>{@code from} and {@code to} are calendar days (inclusive) in {@code zone}, the reader's
 * own time zone; they default to the last 30 days ending today.
 */
@RestController
@RequestMapping("/v1/controller/reports")
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/summary")
    public ReportResponse summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String zone) {
        return reports.summary(ReportService.range(from, to, zone));
    }

    @GetMapping("/incidents.csv")
    public ResponseEntity<byte[]> incidentsCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String zone) {
        ReportService.Range range = ReportService.range(from, to, zone);
        String filename = "techconnect-incidents-" + range.from() + "-to-" + range.to() + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(reports.incidentsCsv(range));
    }
}
