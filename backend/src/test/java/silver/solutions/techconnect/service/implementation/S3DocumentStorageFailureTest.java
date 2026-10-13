package silver.solutions.techconnect.service.implementation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import silver.solutions.techconnect.config.TechConnectProperties;
import silver.solutions.techconnect.exception.ApiException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * What the storage adapter does when AWS cannot be used at all, as opposed to when S3 itself
 * answers with an error (covered against LocalStack in the integration test).
 *
 * <p>The case that mattered: a service started without an IAM task role has no credentials, and
 * the SDK reports that as {@link SdkClientException}, not {@link S3Exception}. That used to escape
 * as a 500 with no useful message; it must be the same calm 503 as any other storage failure.
 */
class S3DocumentStorageFailureTest {

    private S3Client s3;
    private S3DocumentStorage storage;

    @BeforeEach
    void setUp() {
        s3 = mock(S3Client.class);
        var properties = new TechConnectProperties(
                null, null, null, null, null, null,
                new TechConnectProperties.Aws("eu-west-1", "", "techconnect-applications"),
                null, null, null);
        storage = new S3DocumentStorage(s3, properties);
    }

    private static SdkClientException noCredentials() {
        return SdkClientException.create(
                "Unable to load credentials from any of the providers in the chain");
    }

    @Test
    void missingCredentialsWhenStoringIsAFriendly503() {
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(noCredentials());

        assertThatThrownBy(() -> storage.put("applications/a", new byte[] {1}, "application/pdf"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(e.getMessage()).contains("couldn't store your documents");
                });
    }

    @Test
    void anS3ErrorWhenStoringIsTheSame503() {
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message("NoSuchBucket").statusCode(404).build());

        assertThatThrownBy(() -> storage.put("applications/a", new byte[] {1}, "application/pdf"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void missingCredentialsWhenReadingIsAFriendly503() {
        when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(noCredentials());

        assertThatThrownBy(() -> storage.get("applications/a"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(e.getMessage()).contains("document store is unavailable");
                });
    }

    @Test
    void cleaningUpNeverThrowsEvenWhenAwsIsUnreachable() {
        when(s3.deleteObject(any(DeleteObjectRequest.class))).thenThrow(noCredentials());

        assertThatCode(() -> storage.delete("applications/a")).doesNotThrowAnyException();
    }
}
