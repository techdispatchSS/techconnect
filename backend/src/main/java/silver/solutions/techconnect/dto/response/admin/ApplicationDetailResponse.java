package silver.solutions.techconnect.dto.response.admin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import silver.solutions.techconnect.dto.response.common.AddressResponse;
import silver.solutions.techconnect.entity.ApplicationStatus;

public record ApplicationDetailResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String phone,
        /** South African ID number: personal information, Manager-only. */
        String idNumber,
        AddressResponse address,
        String experienceSummary,
        String skills,
        ApplicationStatus status,
        List<ApplicationDocumentResponse> documents,
        String decidedByName,
        Instant decidedAt,
        String decisionReason,
        /** The account created on approval; null otherwise. */
        UUID userId,
        Instant submittedAt) {}
