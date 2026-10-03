package silver.solutions.techconnect.application;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import silver.solutions.techconnect.exception.ApiException;
import silver.solutions.techconnect.service.DocumentStorage;

/** Stands in for S3 in flow tests (S3 itself is covered by S3DocumentStorageIntegrationTest). */
class InMemoryDocumentStorage implements DocumentStorage {

    final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    /** When set, the Nth upload (1-based) fails, to prove a half-stored submission is undone. */
    volatile int failOnPutNumber = 0;

    private int puts = 0;

    @Override
    public synchronized void put(String key, byte[] content, String contentType) {
        puts++;
        if (failOnPutNumber > 0 && puts == failOnPutNumber) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "storage down");
        }
        objects.put(key, content);
    }

    @Override
    public byte[] get(String key) {
        byte[] bytes = objects.get(key);
        if (bytes == null) {
            throw ApiException.notFound("gone");
        }
        return bytes;
    }

    @Override
    public void delete(String key) {
        objects.remove(key);
    }

    synchronized void reset() {
        objects.clear();
        puts = 0;
        failOnPutNumber = 0;
    }
}
