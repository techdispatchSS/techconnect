package silver.solutions.techconnect.config;

import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Phase 2 (photo/document storage, PRD §7) AWS wiring — S3 client only for now (#4); SQS/SNS
 * get added alongside whatever feature first needs them.
 *
 * <p>{@code techconnect.aws.endpoint-override} is what switches this between LocalStack and
 * real AWS: blank in every deployed environment (see application-prod.properties, which sets
 * no override at all, and application.properties' own blank default), pointed at
 * {@code http://localhost:4566} by application-dev.properties for local, non-Docker runs, and
 * at the {@code localstack} service by docker-compose.yml when the backend itself is
 * containerised.
 */
@Configuration
public class S3ClientConfig {

    @Bean
    S3Client s3Client(TechConnectProperties properties) {
        return buildS3Client(properties.aws());
    }

    /**
     * Package-private so the integration test can build a client against a Testcontainers
     * LocalStack instance using the exact same production wiring, instead of duplicating it.
     */
    static S3Client buildS3Client(TechConnectProperties.Aws aws) {
        var builder = S3Client.builder().region(Region.of(aws.region()));

        String endpointOverride = aws.endpointOverride();
        if (endpointOverride != null && !endpointOverride.isBlank()) {
            // LocalStack accepts any credentials — it never checks them against a real
            // account — but the SDK still refuses to sign a request with none configured, so a
            // fixed placeholder pair stands in for them locally.
            builder.endpointOverride(URI.create(endpointOverride))
                    .credentialsProvider(
                            StaticCredentialsProvider.create(
                                    AwsBasicCredentials.create("localstack", "localstack")))
                    // Path-style (http://host:4566/bucket/key) rather than virtual-hosted-style
                    // (http://bucket.host:4566/key): LocalStack's single endpoint doesn't do
                    // per-bucket DNS, so virtual-hosted addressing simply fails to resolve.
                    .forcePathStyle(true);
        } else {
            // Real AWS: the SDK's default chain (env vars, shared config/credentials files, or
            // — in a real deployment — the ECS/EC2 task role) resolves the actual credentials.
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        return builder.build();
    }
}
