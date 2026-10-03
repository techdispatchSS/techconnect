package silver.solutions.techconnect.service;

/**
 * Private object storage for applicants' uploaded proof of certificates and qualifications.
 *
 * <p>Objects are never publicly addressable: nothing hands out a storage URL, and every read
 * goes through an authenticated Manager endpoint. Keys are random, so they reveal nothing about
 * the applicant or the original filename.
 */
public interface DocumentStorage {

    void put(String key, byte[] content, String contentType);

    byte[] get(String key);

    /** Best-effort: used to undo an upload when the rest of a submission fails. */
    void delete(String key);
}
