package silver.solutions.techconnect.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Proof of a certificate or qualification, uploaded with an application. The file lives in
 * private object storage under {@link #storageKey}; this row holds only its metadata and a
 * SHA-256 fingerprint. {@link #contentType} is what the file's own bytes were detected as, never
 * what the client claimed, and {@link #originalFilename} is sanitised display text that is
 * never used to build a storage path.
 */
@Entity
@Table(name = "application_documents")
@Getter
@Setter
@NoArgsConstructor
public class ApplicationDocument {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentKind kind;

    @Column(nullable = false)
    private String title;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private String sha256;

    @Column(name = "storage_key", nullable = false, updatable = false)
    private String storageKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
