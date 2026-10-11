package silver.solutions.techconnect.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import silver.solutions.techconnect.config.TechConnectProperties;
import silver.solutions.techconnect.exception.ApiException;
import silver.solutions.techconnect.service.implementation.S3DocumentStorage;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;

/** The real S3 adapter against a real S3 API (LocalStack), not a mock. */
@Testcontainers
class S3DocumentStorageIntegrationTest {

    private static final String BUCKET = "techconnect-applications-test";

    @Container
    static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8"))
                    .withServices(LocalStackContainer.Service.S3);

    private S3Client client() {
        return S3Client.builder()
                .endpointOverride(LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.S3))
                .region(Region.of(LOCALSTACK.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
                .forcePathStyle(true)
                .build();
    }

    private S3DocumentStorage storage(S3Client s3) {
        s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        var props = new TechConnectProperties(
                null, null, null, null, null, null,
                new TechConnectProperties.Aws("us-east-1", "", BUCKET), null, null, null);
        return new S3DocumentStorage(s3, props);
    }

    @Test
    void storesReadsBackAndDeletesAnEncryptedAttachmentObject() {
        try (S3Client s3 = client()) {
            S3DocumentStorage storage = storage(s3);
            byte[] content = "%PDF-1.4 hello".getBytes();

            storage.put("applications/abc", content, "application/pdf");
            assertThat(storage.get("applications/abc")).isEqualTo(content);

            var head = s3.headObject(HeadObjectRequest.builder().bucket(BUCKET).key("applications/abc").build());
            assertThat(head.serverSideEncryptionAsString()).isEqualTo("AES256");
            assertThat(head.contentDisposition()).isEqualTo("attachment");
            assertThat(head.cacheControl()).contains("no-store");

            storage.delete("applications/abc");
            assertThatThrownBy(() -> storage.get("applications/abc")).isInstanceOf(ApiException.class);
        }
    }

    @Test
    void deletingSomethingThatIsNotThereIsHarmless() {
        try (S3Client s3 = client()) {
            storage(s3).delete("applications/never-existed");
        }
    }
}
