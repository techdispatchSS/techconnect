package silver.solutions.techconnect.dto.response.admin;

import java.time.Instant;
import java.util.UUID;
import silver.solutions.techconnect.entity.ApplicationStatus;

public record ApplicationSummaryResponse(
        UUID id,
        String name,
        String email,
        String phone,
        ApplicationStatus status,
        int documentCount,
        Instant submittedAt,
        Instant decidedAt) {}
