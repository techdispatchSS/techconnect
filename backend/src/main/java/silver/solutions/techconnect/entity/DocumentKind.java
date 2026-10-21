package silver.solutions.techconnect.entity;

import java.util.List;

/** What an applicant's uploaded proof is evidence of. */
public enum DocumentKind {
    /**
     * Required: a national ID card, passport or driver's licence, every side or page. Stored as
     * {@code ID_DOCUMENT} so nothing on record changes; it reads as an identification document.
     */
    ID_DOCUMENT("identification document"),
    /**
     * Required: their high school diploma or leaving certificate (the matric certificate in
     * South Africa). Stored as {@code MATRIC_CERTIFICATE} for the same reason.
     */
    MATRIC_CERTIFICATE("high school diploma"),
    /** Required: something showing where they live (a utility bill, a lease, ...). */
    PROOF_OF_RESIDENCE("proof of residence"),
    /** Optional, and the applicant names each one: a trade or vendor certification. */
    CERTIFICATE("certification"),
    /** No longer offered on the form; kept so applications that already carry one still load. */
    QUALIFICATION("qualification");

    /** Every application must carry at least one file for each of these (up to {@link #MAX_PER_REQUIRED}). */
    public static final List<DocumentKind> REQUIRED =
            List.of(ID_DOCUMENT, MATRIC_CERTIFICATE, PROOF_OF_RESIDENCE);

    /** One required document may be several files: both sides of an ID, every page of a bill. */
    public static final int MAX_PER_REQUIRED = 4;

    private final String label;

    DocumentKind(String label) {
        this.label = label;
    }

    /** How the kind reads in a sentence ("Attach your high school diploma."). */
    public String label() {
        return label;
    }
}
