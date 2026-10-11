package silver.solutions.techconnect.dto.response.admin;

import java.util.UUID;
import silver.solutions.techconnect.entity.DocumentKind;

public record ApplicationDocumentResponse(
        UUID id,
        DocumentKind kind,
        String title,
        String filename,
        String contentType,
        long sizeBytes) {}
