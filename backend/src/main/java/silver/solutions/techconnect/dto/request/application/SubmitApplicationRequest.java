package silver.solutions.techconnect.dto.request.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import silver.solutions.techconnect.dto.request.common.AddressRequest;
import silver.solutions.techconnect.entity.DocumentKind;

/**
 * The JSON half of a public technician application (the proof-of-qualification files travel as
 * separate multipart parts, matched to {@link #documents()} by position).
 *
 * <p>Everything here comes from an anonymous caller, so every field has a tight shape and
 * length, not just a "not blank" check: names are letters and ordinary punctuation only, the
 * email cannot contain whitespace or header-injection characters, and free text is length-capped.
 */
public record SubmitApplicationRequest(
        @NotBlank @Size(max = 100)
                @Pattern(
                        regexp = "^[\\p{L}][\\p{L} .'’\\-]*$",
                        message = "may only contain letters, spaces . ' and -")
                String firstName,
        @NotBlank @Size(max = 100)
                @Pattern(
                        regexp = "^[\\p{L}][\\p{L} .'’\\-]*$",
                        message = "may only contain letters, spaces . ' and -")
                String lastName,
        @NotBlank @Email @Size(max = 254)
                @Pattern(regexp = "^\\S+$", message = "must not contain spaces")
                String email,
        @NotBlank
                @Pattern(
                        regexp = "^\\+?[0-9][0-9 ()\\-]{7,18}[0-9]$",
                        message = "must be a valid phone number")
                String phone,
        @NotNull @Valid AddressRequest address,
        @NotBlank @Size(min = 20, max = 4000) String experienceSummary,
        @Size(max = 1000) String skills,
        @NotEmpty @Size(max = 10) List<@NotNull @Valid DocumentMeta> documents,
        @AssertTrue(message = "You must accept the privacy notice to apply") boolean consent,
        /** Honeypot: invisible to people, tempting to bots. Anything in here is a bot. */
        String website) {

    /** What one attached file is evidence of, in the applicant's own words. */
    public record DocumentMeta(
            @NotNull DocumentKind kind, @NotBlank @Size(max = 200) String title) {}
}
