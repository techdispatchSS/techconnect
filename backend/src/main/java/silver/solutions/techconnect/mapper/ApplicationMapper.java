package silver.solutions.techconnect.mapper;

import java.util.List;
import org.springframework.stereotype.Component;
import silver.solutions.techconnect.dto.response.admin.ApplicationDetailResponse;
import silver.solutions.techconnect.dto.response.admin.ApplicationDocumentResponse;
import silver.solutions.techconnect.dto.response.admin.ApplicationSummaryResponse;
import silver.solutions.techconnect.entity.ApplicationDocument;
import silver.solutions.techconnect.entity.TechnicianApplication;

@Component
public class ApplicationMapper {

    private final AddressMapper addressMapper;

    public ApplicationMapper(AddressMapper addressMapper) {
        this.addressMapper = addressMapper;
    }

    public ApplicationSummaryResponse toSummary(TechnicianApplication application, int documentCount) {
        return new ApplicationSummaryResponse(
                application.getId(),
                application.fullName(),
                application.getEmail(),
                application.getPhone(),
                application.getStatus(),
                documentCount,
                application.getCreatedAt(),
                application.getDecidedAt());
    }

    public ApplicationDetailResponse toDetail(
            TechnicianApplication application,
            List<ApplicationDocument> documents,
            String decidedByName) {

        return new ApplicationDetailResponse(
                application.getId(),
                application.getFirstName(),
                application.getLastName(),
                application.getEmail(),
                application.getPhone(),
                application.getIdNumber(),
                addressMapper.toAddressResponse(application.getAddress()).orElse(null),
                application.getExperienceSummary(),
                application.getSkills(),
                application.getStatus(),
                documents.stream().map(this::toDocument).toList(),
                decidedByName,
                application.getDecidedAt(),
                application.getDecisionReason(),
                application.getUserId(),
                application.getCreatedAt());
    }

    public ApplicationDocumentResponse toDocument(ApplicationDocument document) {
        return new ApplicationDocumentResponse(
                document.getId(),
                document.getKind(),
                document.getTitle(),
                document.getOriginalFilename(),
                document.getContentType(),
                document.getSizeBytes());
    }
}
