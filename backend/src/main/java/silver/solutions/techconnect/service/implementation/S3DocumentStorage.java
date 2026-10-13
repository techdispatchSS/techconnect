package silver.solutions.techconnect.service.implementation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import silver.solutions.techconnect.config.TechConnectProperties;
import silver.solutions.techconnect.exception.ApiException;
import silver.solutions.techconnect.service.DocumentStorage;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

/**
 * S3-backed {@link DocumentStorage} (the client from {@code S3ClientConfig}; LocalStack locally,
 * real AWS when deployed). Every object is encrypted at rest, stored as an attachment, and marked
 * non-cacheable. The bucket itself must also have "Block all public access" on in AWS; that is
 * infrastructure, not something application code can enforce.
 */
@Service
public class S3DocumentStorage implements DocumentStorage {

    private static final Logger log = LoggerFactory.getLogger(S3DocumentStorage.class);

    private final S3Client s3;
    private final String bucket;

    public S3DocumentStorage(S3Client s3, TechConnectProperties properties) {
        this.s3 = s3;
        this.bucket = properties.aws().documentsBucket();
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        try {
            s3.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .contentDisposition("attachment")
                            .cacheControl("private, no-store")
                            .serverSideEncryption(ServerSideEncryption.AES256)
                            .build(),
                    RequestBody.fromBytes(content));
        } catch (SdkException e) {
            // S3Exception (the bucket refused or is missing) and SdkClientException (no
            // credentials, no route to AWS, a timeout) are both SdkException. Only the first was
            // caught, so a service with no task role or a wrong region answered 500 instead of
            // this message, and the real cause was buried in a stack trace.
            log.error("Could not store document {} in bucket {}: {}", key, bucket, e.toString(), e);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "We couldn't store your documents right now. Please try again shortly.");
        }
    }

    @Override
    public byte[] get(String key) {
        try {
            return s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build())
                    .asByteArray();
        } catch (NoSuchKeyException e) {
            throw ApiException.notFound("That document is no longer available.");
        } catch (SdkException e) {
            log.error("Could not read document {} from bucket {}: {}", key, bucket, e.toString(), e);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The document store is unavailable. Please try again shortly.");
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (RuntimeException e) {
            // An orphaned object costs a little storage; failing the caller's cleanup path
            // would mask the real error they are already handling.
            log.warn("Could not remove orphaned document {}", key, e);
        }
    }
}
