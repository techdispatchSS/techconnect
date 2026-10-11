package silver.solutions.techconnect.dto.response.application;

/**
 * The same body for every accepted submission — including ones silently discarded because the
 * person has already applied. Anything that differed would let the form be used to discover
 * who has applied (or been rejected) before.
 */
public record ApplicationReceivedResponse(String message) {

    public static ApplicationReceivedResponse standard() {
        return new ApplicationReceivedResponse(
                "Thank you. If this is your first application, we have sent a confirmation to "
                        + "your email and a manager will review it. You will hear from us by "
                        + "email once a decision has been made.");
    }
}
