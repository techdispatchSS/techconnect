package silver.solutions.techconnect.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Proves the {@link S3ClientConfig} wiring end-to-end (#4's "one smoke-tested usage") against a
 * real S3 API — a Testcontainers LocalStack instance, not the developer's already-running
 * docker-compose one, the same reasoning {@code AbstractPostgresIntegrationTest} uses for the
 * database. Same image tag as docker-compose.yml's `localstack` service.
 */
@Testcontainers
class S3ClientConfigIntegrationTest {

    @Container
    static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8"))
                    .withServices(LocalStackContainer.Service.S3);

    @Test
    void uploadsAndReadsBackAnObjectThroughTheConfiguredClient() {
        var aws =
                new TechConnectProperties.Aws(
                        LOCALSTACK.getRegion(),
                        LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.S3).toString(),
                        "techconnect-smoke-test");

        try (S3Client client = S3ClientConfig.buildS3Client(aws)) {
            client.createBucket(CreateBucketRequest.builder().bucket(aws.documentsBucket()).build());

            String key = "smoke-test.txt";
            String content = "TechConnect S3 wiring works.";
            client.putObject(
                    PutObjectRequest.builder().bucket(aws.documentsBucket()).key(key).build(),
                    RequestBody.fromString(content, StandardCharsets.UTF_8));

            byte[] roundTripped =
                    client.getObjectAsBytes(
                                    GetObjectRequest.builder()
                                            .bucket(aws.documentsBucket())
                                            .key(key)
                                            .build())
                            .asByteArray();

            assertThat(new String(roundTripped, StandardCharsets.UTF_8)).isEqualTo(content);
        }
    }
}
