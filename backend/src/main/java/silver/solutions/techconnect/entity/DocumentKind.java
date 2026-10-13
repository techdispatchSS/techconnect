package silver.solutions.techconnect.entity;

import java.util.List;

/** What an applicant's uploaded proof is evidence of. */
public enum DocumentKind {
    /** Required: a copy of their ID. */
    ID_DOCUMENT("ID document"),
    /** Required: their matric certificate. */
    MATRIC_CERTIFICATE("matric certificate"),
    /** Required: something showing where they live (a utility bill, a lease, ...). */
    PROOF_OF_RESIDENCE("proof of residence"),
    /** Optional, and the applicant names each one: a trade or vendor certification. */
    CERTIFICATE("certification"),
    /** No longer offered on the form; kept so applications that already carry one still load. */
    QUALIFICATION("qualification");

    /** Every application must carry exactly one of each of these. */
    public static final List<DocumentKind> REQUIRED =
            List.of(ID_DOCUMENT, MATRIC_CERTIFICATE, PROOF_OF_RESIDENCE);

    private final String label;

    DocumentKind(String label) {
        this.label = label;
    }

    /** How the kind reads in a sentence ("Attach your matric certificate."). */
    public String label() {
        return label;
    }
}
