package silver.solutions.techconnect.entity;

/** A technician application's outcome. Only a Manager moves it out of {@link #PENDING}. */
public enum ApplicationStatus {
    PENDING,
    APPROVED,
    /** Permanent: the applicant's record is kept so they cannot simply apply again. */
    REJECTED
}
