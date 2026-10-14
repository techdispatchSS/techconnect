package silver.solutions.techconnect.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import silver.solutions.techconnect.AbstractPostgresIntegrationTest;
import silver.solutions.techconnect.entity.AdminAuditAction;
import silver.solutions.techconnect.entity.ApplicationStatus;
import silver.solutions.techconnect.entity.DocumentKind;
import silver.solutions.techconnect.entity.TechnicianApplication;
import silver.solutions.techconnect.entity.User;
import silver.solutions.techconnect.entity.UserRole;
import silver.solutions.techconnect.entity.UserStatus;
import silver.solutions.techconnect.repository.AdminAuditLogRepository;
import silver.solutions.techconnect.repository.ApplicationDocumentRepository;
import silver.solutions.techconnect.repository.TechnicianApplicationRepository;
import silver.solutions.techconnect.repository.TechnicianProfileRepository;
import silver.solutions.techconnect.repository.UserRepository;
import silver.solutions.techconnect.service.NotificationService;

/**
 * The technician-application flow end to end against a real PostgreSQL: the public submission,
 * the Manager-only review, and — the part that matters most — that a rejection is permanent and
 * that hostile input is refused.
 *
 * <p>Not {@code @Transactional} (the approve/reject paths take row locks and commit), so every
 * test uses its own unique email address and phone number instead of relying on rollback.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    // The per-address throttle has its own test; here it would trip after five submissions.
    "techconnect.applications.per-ip-per-hour=100000",
    "techconnect.applications.global-per-hour=1000000"
})
class TechnicianApplicationIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String PASSWORD = "ManagerPassw0rd!23";

    @TestConfiguration
    static class StorageConfig {
        @Bean
        @Primary
        InMemoryDocumentStorage inMemoryDocumentStorage() {
            return new InMemoryDocumentStorage();
        }
    }

    @Autowired private MockMvc mvc;
    @Autowired private InMemoryDocumentStorage storage;
    @Autowired private TechnicianApplicationRepository applications;
    @Autowired private ApplicationDocumentRepository documents;
    @Autowired private UserRepository users;
    @Autowired private TechnicianProfileRepository technicianProfiles;
    @Autowired private AdminAuditLogRepository auditLog;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoBean private NotificationService notifications;

    @BeforeEach
    void resetStorage() {
        storage.reset();
    }

    // --- submitting ------------------------------------------------------------------------

    @Test
    void acceptsAnApplicationWithProofAndStoresTheDocuments() throws Exception {
        Applicant a = Applicant.unique("accepts");

        submit(a, file("CCNA certificate", "CERTIFICATE", "My CCNA.pdf", "application/pdf",
                        TestFiles.pdf()),
                file("Diploma", "QUALIFICATION", "diploma.png", "image/png", TestFiles.png()))
                .andExpect(status().isAccepted());

        TechnicianApplication saved = applications.findAll().stream()
                .filter(x -> x.getEmail().equals(a.email))
                .findFirst().orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ApplicationStatus.PENDING);
        assertThat(saved.getFirstName()).isEqualTo("Nomsa");
        assertThat(saved.getAddress().getCity()).isEqualTo("Durban");

        var docs = documents.findByApplicationIdOrderByCreatedAtAsc(saved.getId());
        assertThat(docs).hasSize(5);
        assertThat(docs).extracting("kind").containsExactly(
                DocumentKind.ID_DOCUMENT, DocumentKind.MATRIC_CERTIFICATE,
                DocumentKind.PROOF_OF_RESIDENCE, DocumentKind.CERTIFICATE,
                DocumentKind.QUALIFICATION);
        assertThat(docs).extracting("contentType").containsExactly(
                "application/pdf", "application/pdf", "application/pdf", "application/pdf",
                "image/png");
        assertThat(docs).extracting("originalFilename").containsExactly(
                "id.pdf", "matric.pdf", "residence.pdf", "My CCNA.pdf", "diploma.png");
        // Stored under a random key that says nothing about the applicant or the file name.
        assertThat(docs).allSatisfy(d -> {
            assertThat(d.getStorageKey()).matches("applications/[0-9a-f-]{36}");
            assertThat(storage.objects).containsKey(d.getStorageKey());
            assertThat(d.getSha256()).hasSize(64);
        });
        verify(notifications).sendApplicationReceived(a.email, "Nomsa");
    }

    @Test
    void createsNoAccountAndNoLoginForAnApplicant() throws Exception {
        Applicant a = Applicant.unique("noaccount");
        submit(a, validPdf()).andExpect(status().isAccepted());

        assertThat(users.existsByEmailIgnoreCase(a.email)).isFalse();
        mvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(a.email, PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refusesAnApplicationWithNoProofAttached() throws Exception {
        Applicant a = Applicant.unique("noproof");
        mvc.perform(multipartRequest(a, List.of()))
                .andExpect(status().isBadRequest());
        assertThat(exists(a)).isFalse();
    }

    @Test
    void acceptsTheThreeRequiredDocumentsWithNoCertifications() throws Exception {
        Applicant a = Applicant.unique("nocerts");

        mvc.perform(multipartRequest(a, requiredDocuments())).andExpect(status().isAccepted());

        assertThat(documents.findByApplicationIdOrderByCreatedAtAsc(find(a).getId())).hasSize(3);
    }

    @Test
    void refusesAnApplicationMissingARequiredDocument() throws Exception {
        for (DocumentKind missing : DocumentKind.REQUIRED) {
            Applicant a = Applicant.unique("missing-" + missing.name().toLowerCase());
            List<Part> parts = requiredDocuments().stream()
                    .filter(p -> !p.kind.equals(missing.name()))
                    .toList();

            mvc.perform(multipartRequest(a, parts))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value(
                            org.hamcrest.Matchers.containsString(missing.label())));

            assertThat(exists(a)).as("nothing is saved without the " + missing.label()).isFalse();
        }
        assertThat(storage.objects).as("and nothing was uploaded").isEmpty();
    }

    @Test
    void certificationsCannotStandInForTheRequiredDocuments() throws Exception {
        Applicant a = Applicant.unique("certsonly");

        mvc.perform(multipartRequest(a, List.of(validPdf(), validPdf())))
                .andExpect(status().isBadRequest());

        assertThat(exists(a)).isFalse();
    }

    @Test
    void refusesTwoOfTheSameRequiredDocument() throws Exception {
        Applicant a = Applicant.unique("twoids");
        List<Part> parts = new java.util.ArrayList<>(requiredDocuments());
        parts.add(file("ID again", "ID_DOCUMENT", "id2.pdf", "application/pdf", TestFiles.pdf()));

        mvc.perform(multipartRequest(a, parts))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        org.hamcrest.Matchers.containsString("only one")));

        assertThat(exists(a)).isFalse();
    }

    private static final String NAIROBI = """
            {"street":"12 Moi Avenue","city":"Nairobi","province":"Nairobi County","country":"KE"}""";

    @Test
    void acceptsAnApplicantInAnotherCountryWithNoSuburbOrPostcodeAndANonSouthAfricanId() throws Exception {
        Applicant a = Applicant.unique("nairobi");
        a.address = NAIROBI;
        a.idNumber = "A1234567";
        a.phone = "0712 345 678";

        submit(a, validPdf()).andExpect(status().isAccepted());

        TechnicianApplication saved = find(a);
        assertThat(saved.getAddress().getCountry().name()).isEqualTo("KE");
        assertThat(saved.getAddress().getProvince()).isEqualTo("Nairobi County");
        assertThat(saved.getAddress().getSuburb()).isNull();
        assertThat(saved.getAddress().getPostalCode()).isNull();
        assertThat(saved.getIdNumber()).isEqualTo("A1234567");
        assertThat(saved.getPhoneNormalized()).startsWith("254712");
    }

    @Test
    void aSouthAfricanApplicantStillNeedsARealSouthAfricanId() throws Exception {
        Applicant a = Applicant.unique("badza");
        a.idNumber = "A1234567";

        mvc.perform(multipartRequest(a, allWith(validPdf())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        org.hamcrest.Matchers.containsString("South African ID")));

        assertThat(exists(a)).isFalse();
    }

    @Test
    void theSamePersonCannotReapplyFromTheirOwnCountryByWritingTheNumberAnotherWay() throws Exception {
        Applicant first = Applicant.unique("kenya-a");
        first.address = NAIROBI;
        first.idNumber = "K" + (long) (Math.random() * 9_000_000) + 1_000_000;
        first.phone = "+254 722 111 222";
        submit(first, validPdf()).andExpect(status().isAccepted());
        long before = applications.count();

        Applicant again = Applicant.unique("kenya-b");
        again.address = NAIROBI;
        again.idNumber = "K" + (long) (Math.random() * 9_000_000) + 2_000_000;
        again.phone = "0722 111 222";
        submit(again, validPdf()).andExpect(status().isAccepted());

        assertThat(applications.count()).as("quietly discarded: same number written locally")
                .isEqualTo(before);
        assertThat(exists(again)).isFalse();
    }

    @Test
    void anInvalidProvinceForSouthAfricaOrAMalformedPostcodeIsRefused() throws Exception {
        Applicant a = Applicant.unique("badprovince");
        a.address = """
                {"street":"1 Test Street","suburb":"Umhlanga","city":"Durban",
                 "province":"ATLANTIS","postalCode":"4001"}""";

        mvc.perform(multipartRequest(a, allWith(validPdf()))).andExpect(status().isBadRequest());

        Applicant b = Applicant.unique("badpostcode");
        b.address = """
                {"street":"1 St","city":"Accra","province":"Greater Accra","postalCode":"<script>","country":"GH"}""";
        mvc.perform(multipartRequest(b, allWith(validPdf()))).andExpect(status().isBadRequest());

        assertThat(exists(a)).isFalse();
        assertThat(exists(b)).isFalse();
    }

    @Test
    void refusesWithoutConsent() throws Exception {
        Applicant a = Applicant.unique("noconsent");
        a.consent = false;
        submit(a, validPdf()).andExpect(status().isBadRequest());
        assertThat(exists(a)).isFalse();
    }

    @Test
    void refusesFilesThatAreNotWhatTheyClaimToBe() throws Exception {
        Applicant a = Applicant.unique("fakefiles");

        // A script renamed to .pdf.
        submit(a, file("Cert", "CERTIFICATE", "cert.pdf", "application/pdf",
                "#!/bin/sh\necho pwned\n".repeat(20).getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest());

        // Real PNG bytes, but labelled as a PDF.
        submit(a, file("Cert", "CERTIFICATE", "cert.pdf", "application/pdf", TestFiles.png()))
                .andExpect(status().isBadRequest());

        // Real PDF, but an executable extension.
        submit(a, file("Cert", "CERTIFICATE", "cert.exe", "application/pdf", TestFiles.pdf()))
                .andExpect(status().isBadRequest());

        // Real PDF, but the client lies about the content type.
        submit(a, file("Cert", "CERTIFICATE", "cert.pdf", "text/html", TestFiles.pdf()))
                .andExpect(status().isBadRequest());

        assertThat(exists(a)).isFalse();
        assertThat(storage.objects).isEmpty();
    }

    @Test
    void refusesPdfsCarryingScriptsIncludingObfuscatedOnes() throws Exception {
        Applicant a = Applicant.unique("pdfjs");

        submit(a, file("Cert", "CERTIFICATE", "cert.pdf", "application/pdf",
                TestFiles.pdfWith("3 0 obj\n<< /S /JavaScript /JS (app.alert(1)) >>\nendobj\n")))
                .andExpect(status().isBadRequest());

        // /JavaScript spelled with a #61 escape for the 'a' — a classic scanner dodge.
        submit(a, file("Cert", "CERTIFICATE", "cert.pdf", "application/pdf",
                TestFiles.pdfWith("3 0 obj\n<< /S /J#61vaScript >>\nendobj\n")))
                .andExpect(status().isBadRequest());

        submit(a, file("Cert", "CERTIFICATE", "cert.pdf", "application/pdf",
                TestFiles.pdfWith("<< /OpenAction << /S /Launch /F (cmd.exe) >> >>")))
                .andExpect(status().isBadRequest());

        assertThat(exists(a)).isFalse();
    }

    @Test
    void refusesAnImageWithAPayloadAppendedAfterItsEnd() throws Exception {
        Applicant a = Applicant.unique("polyglot");
        submit(a, file("Cert", "CERTIFICATE", "scan.png", "image/png",
                TestFiles.withTrailingBytes(TestFiles.png(), "<script>alert(1)</script>")))
                .andExpect(status().isBadRequest());
        submit(a, file("Cert", "CERTIFICATE", "scan.jpg", "image/jpeg",
                TestFiles.withTrailingBytes(TestFiles.jpeg(), "<?php system($_GET['c']); ?>")))
                .andExpect(status().isBadRequest());
        assertThat(exists(a)).isFalse();
    }

    @Test
    void refusesAFileOverTheSizeLimit() throws Exception {
        Applicant a = Applicant.unique("toobig");
        byte[] huge = TestFiles.withTrailingBytes(TestFiles.pdf(), "x".repeat(10 * 1024 * 1024));
        submit(a, file("Cert", "CERTIFICATE", "big.pdf", "application/pdf", huge))
                .andExpect(status().isContentTooLarge());
        assertThat(exists(a)).isFalse();
    }

    @Test
    void refusesMalformedAndHostileFields() throws Exception {
        Applicant a = Applicant.unique("hostile");

        a.firstName = "<script>alert(1)</script>";
        submit(a, validPdf()).andExpect(status().isBadRequest());

        a = Applicant.unique("hostile2");
        a.email = "bob@example.com\r\nBcc: victim@example.com";
        submit(a, validPdf()).andExpect(status().isBadRequest());

        a = Applicant.unique("hostile3");
        a.phone = "not a phone";
        submit(a, validPdf()).andExpect(status().isBadRequest());

        a = Applicant.unique("hostile4");
        a.experience = "too short";
        submit(a, validPdf()).andExpect(status().isBadRequest());
    }

    @Test
    void stripsControlCharactersFromFreeText() throws Exception {
        Applicant a = Applicant.unique("control");
        a.experience = "Ten years of structured cabling\u0000 and \u001b[31mfibre\u001b[0m work.";
        submit(a, validPdf()).andExpect(status().isAccepted());

        TechnicianApplication saved = find(a);
        assertThat(saved.getExperienceSummary()).doesNotContain("\u0000").doesNotContain("\u001b");
    }

    @Test
    void quietlyDiscardsABotThatFillsTheHoneypot() throws Exception {
        Applicant a = Applicant.unique("bot");
        a.website = "http://spam.example";
        submit(a, validPdf()).andExpect(status().isAccepted());

        assertThat(exists(a)).isFalse();
        assertThat(storage.objects).isEmpty();
        verify(notifications, never()).sendApplicationReceived(a.email, "Nomsa");
    }

    @Test
    void undoesTheUploadsIfStoringTheNextFileFails() throws Exception {
        Applicant a = Applicant.unique("halfway");
        storage.failOnPutNumber = 2;

        submit(a, validPdf(), file("Diploma", "QUALIFICATION", "d.png", "image/png", TestFiles.png()))
                .andExpect(status().isServiceUnavailable());

        assertThat(exists(a)).isFalse();
        assertThat(storage.objects).as("the first, successfully stored file is removed again").isEmpty();
    }

    @Test
    void aStaleSessionTokenDoesNotStopAPublicSubmission() throws Exception {
        Applicant a = Applicant.unique("stale");

        // A browser that still holds an expired or revoked session token sends it along.
        int status = mvc.perform(multipartRequest(a, List.of(validPdf()))
                        .header("Authorization", "Bearer not.a.valid.token"))
                .andReturn().getResponse().getStatus();

        assertThat(status).as("the public form never asks who you are")
                .isNotIn(401, 403);

        // ...while the same token is still refused where a session really is required.
        mvc.perform(get("/v1/admin/applications")
                        .header("Authorization", "Bearer not.a.valid.token"))
                .andExpect(status().isUnauthorized());
    }

    // --- one application per person, forever -----------------------------------------------

    @Test
    void aRepeatApplicationIsSilentlyDiscardedAndSendsNoEmail() throws Exception {
        Applicant a = Applicant.unique("repeat");
        submit(a, validPdf()).andExpect(status().isAccepted());
        long before = applications.count();

        // Same response either way — the caller can't tell it was discarded.
        String first = submit(a, validPdf()).andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        assertThat(first).contains("If this is your first application");

        assertThat(applications.count()).isEqualTo(before);
        verify(notifications, times(1)).sendApplicationReceived(a.email, "Nomsa");
    }

    @Test
    void aRejectedApplicantCanNeverApplyAgain() throws Exception {
        String token = managerToken();
        Applicant a = Applicant.unique("rejected");
        submit(a, validPdf()).andExpect(status().isAccepted());
        UUID id = find(a).getId();

        reject(token, id, "Qualifications could not be verified").andExpect(status().isOk());
        long before = applications.count();

        // Same details again.
        submit(a, validPdf()).andExpect(status().isAccepted());

        // Same email in a different disguise (case, +tag).
        Applicant disguised = Applicant.unique("other");
        disguised.email = a.email.replace("@", "+retry@").toUpperCase();
        submit(disguised, validPdf()).andExpect(status().isAccepted());

        // Different email, same phone number in another format.
        Applicant samePhone = Applicant.unique("phone");
        samePhone.phone = a.phone.replace("+27", "0").replace(" ", "");
        submit(samePhone, validPdf()).andExpect(status().isAccepted());

        assertThat(applications.count()).as("none of the retries created an application")
                .isEqualTo(before);
        assertThat(find(a).getStatus()).isEqualTo(ApplicationStatus.REJECTED);
    }

    @Test
    void anExistingAccountHolderIsQuietlyDiscarded() throws Exception {
        String email = "existing-" + UUID.randomUUID() + "@techconnect.test";
        user(email, UserRole.CONTROLLER);

        Applicant a = Applicant.unique("existing");
        a.email = email;
        long before = applications.count();
        submit(a, validPdf()).andExpect(status().isAccepted());

        assertThat(applications.count()).isEqualTo(before);
    }

    @Test
    void refusesAnIdNumberThatIsNotWellFormed() throws Exception {
        String valid = SaIds.random();
        char last = valid.charAt(12);
        String badChecksum = valid.substring(0, 12) + (char) ('0' + (last - '0' + 1) % 10);

        for (String bad : new String[] {badChecksum, valid.substring(0, 12), valid + "0",
                "90010150000A8", "", "900230500008" + "0"}) {
            Applicant a = Applicant.unique("badid");
            a.idNumber = bad;
            submit(a, validPdf()).andExpect(status().isBadRequest());
            assertThat(exists(a)).as("id '%s'", bad).isFalse();
        }
    }

    @Test
    void aRejectedApplicantCannotReturnEvenWithANewEmailAndANewPhoneNumber() throws Exception {
        String token = managerToken();
        Applicant original = Applicant.unique("idblock");
        submit(original, validPdf()).andExpect(status().isAccepted());
        reject(token, find(original).getId(), "Not suitable").andExpect(status().isOk());
        long before = applications.count();

        // Brand-new email and brand-new phone number: only the ID number gives them away.
        Applicant returning = Applicant.unique("idblock-again");
        returning.idNumber = original.idNumber;
        submit(returning, validPdf()).andExpect(status().isAccepted());

        assertThat(applications.count()).isEqualTo(before);
        assertThat(exists(returning)).isFalse();
        assertThat(storage.objects).as("no files were stored for the blocked attempt")
                .hasSize(documents.findByApplicationIdOrderByCreatedAtAsc(find(original).getId()).size());
    }

    // --- who may review --------------------------------------------------------------------

    @Test
    void thePublicCannotListReadOrDecide() throws Exception {
        mvc.perform(get("/v1/admin/applications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/admin/applications/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/v1/admin/applications/" + UUID.randomUUID() + "/approve"))
                .andExpect(status().isUnauthorized());
        // The public path accepts POST only — there is no way to look an application up.
        mvc.perform(get("/v1/applications")).andExpect(status().isUnauthorized());
    }

    @Test
    void onlyAManagerCanAcceptOrRejectNotAControllerOrATechnician() throws Exception {
        Applicant a = Applicant.unique("rbac");
        submit(a, validPdf()).andExpect(status().isAccepted());
        UUID id = find(a).getId();

        for (UserRole role : List.of(UserRole.CONTROLLER, UserRole.TECHNICIAN)) {
            String token = tokenFor(user("rbac-" + role + "-" + UUID.randomUUID() + "@techconnect.test", role));

            mvc.perform(get("/v1/admin/applications").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/v1/admin/applications/" + id).header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/v1/admin/applications/" + id + "/approve")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            reject(token, id, "nope").andExpect(status().isForbidden());
        }

        assertThat(find(a).getStatus()).as("untouched by the non-managers")
                .isEqualTo(ApplicationStatus.PENDING);
    }

    // --- reviewing, approving, rejecting ---------------------------------------------------

    @Test
    void aManagerSeesTheApplicationAndItsDocumentsAndDownloadsThemSafely() throws Exception {
        String token = managerToken();
        Applicant a = Applicant.unique("review");
        submit(a, validPdf()).andExpect(status().isAccepted());
        TechnicianApplication saved = find(a);
        UUID docId = documents.findByApplicationIdOrderByCreatedAtAsc(saved.getId()).getFirst().getId();

        mvc.perform(get("/v1/admin/applications").param("status", "PENDING").param("q", a.email.substring(0, 12))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value(a.email))
                .andExpect(jsonPath("$.content[0].documentCount").value(4));

        mvc.perform(get("/v1/admin/applications/" + saved.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.documents[0].kind").value("ID_DOCUMENT"))
                .andExpect(jsonPath("$.documents[3].title").value("CCNA certificate"))
                .andExpect(jsonPath("$.documents[3].filename").value("cert.pdf"))
                // The Manager sees the applicant's ID number under their details.
                .andExpect(jsonPath("$.idNumber").value(a.idNumber));

        mvc.perform(get("/v1/admin/applications/" + saved.getId() + "/documents/" + docId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.startsWith("attachment")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; sandbox"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));

        assertThat(auditLog.findAll(
                (root, q, cb) -> cb.equal(root.get("action"), AdminAuditAction.APPLICATION_DOCUMENT_VIEWED),
                org.springframework.data.domain.PageRequest.of(0, 50)).getContent())
                .as("opening an applicant's document is itself audited")
                .isNotEmpty();
    }

    @Test
    void aDocumentCannotBeReadThroughSomeoneElsesApplicationId() throws Exception {
        String token = managerToken();
        Applicant one = Applicant.unique("idor1");
        Applicant two = Applicant.unique("idor2");
        submit(one, validPdf()).andExpect(status().isAccepted());
        submit(two, validPdf()).andExpect(status().isAccepted());

        UUID docOfTwo = documents.findByApplicationIdOrderByCreatedAtAsc(find(two).getId()).getFirst().getId();

        mvc.perform(get("/v1/admin/applications/" + find(one).getId() + "/documents/" + docOfTwo)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void anAcceptedTechniciansDocumentsAreReachableFromTheirAccount() throws Exception {
        String token = managerToken();
        Applicant a = Applicant.unique("fromuser");
        submit(a, validPdf()).andExpect(status().isAccepted());
        UUID applicationId = find(a).getId();
        mvc.perform(post("/v1/admin/applications/" + applicationId + "/approve")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        UUID userId = find(a).getUserId();

        var result = mvc.perform(get("/v1/admin/applications/by-user/" + userId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(applicationId.toString()))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.documents.length()").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andReturn();

        // ...and a document found that way can be opened with the existing, audited download.
        UUID docId = documents.findByApplicationIdOrderByCreatedAtAsc(applicationId)
                .getFirst().getId();
        mvc.perform(get("/v1/admin/applications/" + applicationId + "/documents/" + docId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(result.getResponse().getContentAsString()).contains(docId.toString());
    }

    @Test
    void aPersonAddedDirectlyHasNoApplicationOnFile() throws Exception {
        String token = managerToken();
        User technician = user("direct-" + UUID.randomUUID() + "@techconnect.test", UserRole.TECHNICIAN);

        mvc.perform(get("/v1/admin/applications/by-user/" + technician.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyAManagerCanLookUpAnApplicationByAccount() throws Exception {
        User controller = user("ctrl-" + UUID.randomUUID() + "@techconnect.test", UserRole.CONTROLLER);

        mvc.perform(get("/v1/admin/applications/by-user/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + tokenFor(controller)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/v1/admin/applications/by-user/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void approvingProvisionsATechnicianAccountLikeAddUser() throws Exception {
        String token = managerToken();
        Applicant a = Applicant.unique("approve");
        submit(a, validPdf()).andExpect(status().isAccepted());
        UUID id = find(a).getId();

        mvc.perform(post("/v1/admin/applications/" + id + "/approve")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application.status").value("APPROVED"))
                .andExpect(jsonPath("$.activationUrl").value(
                        org.hamcrest.Matchers.containsString("/activate?token=")));

        TechnicianApplication decided = find(a);
        assertThat(decided.getUserId()).isNotNull();
        assertThat(decided.getDecidedBy()).isNotNull();

        User created = users.findById(decided.getUserId()).orElseThrow();
        assertThat(created.getRole()).isEqualTo(UserRole.TECHNICIAN);
        assertThat(created.getStatus()).isEqualTo(UserStatus.PENDING_ACTIVATION);
        assertThat(created.getPasswordHash()).as("no password until they activate").isNull();
        assertThat(created.getAddress().getCity()).isEqualTo("Durban");
        assertThat(technicianProfiles.findById(created.getId())).isPresent();

        verify(notifications).sendActivationInvite(org.mockito.ArgumentMatchers.any(User.class), anyString());
    }

    @Test
    void rejectingNeedsAReasonKeepsTheRecordAndCreatesNoAccount() throws Exception {
        String token = managerToken();
        Applicant a = Applicant.unique("reject");
        submit(a, validPdf()).andExpect(status().isAccepted());
        UUID id = find(a).getId();

        reject(token, id, "   ").andExpect(status().isBadRequest());
        assertThat(find(a).getStatus()).isEqualTo(ApplicationStatus.PENDING);

        reject(token, id, "Certificates are expired").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decisionReason").value("Certificates are expired"));

        TechnicianApplication decided = find(a);
        assertThat(decided.getUserId()).isNull();
        assertThat(users.existsByEmailIgnoreCase(a.email)).isFalse();
        // The record and its proof are kept.
        assertThat(documents.findByApplicationIdOrderByCreatedAtAsc(id)).hasSize(4);
        verify(notifications).sendApplicationRejected(a.email, "Nomsa");
    }

    @Test
    void anApplicationCanOnlyBeDecidedOnce() throws Exception {
        String token = managerToken();
        Applicant a = Applicant.unique("twice");
        submit(a, validPdf()).andExpect(status().isAccepted());
        UUID id = find(a).getId();

        reject(token, id, "No").andExpect(status().isOk());
        reject(token, id, "Changed my mind").andExpect(status().isConflict());
        mvc.perform(post("/v1/admin/applications/" + id + "/approve")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
        assertThat(find(a).getStatus()).isEqualTo(ApplicationStatus.REJECTED);
    }

    @Test
    void theSidebarCountReflectsPendingApplications() throws Exception {
        String token = managerToken();
        long pending = applications.countByStatus(ApplicationStatus.PENDING);
        submit(Applicant.unique("count"), validPdf()).andExpect(status().isAccepted());

        mvc.perform(get("/v1/admin/applications/pending-count").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(pending + 1));
    }

    // --- helpers ---------------------------------------------------------------------------

    private record Part(String title, String kind, String filename, String contentType, byte[] bytes) {}

    private static Part file(String title, String kind, String filename, String type, byte[] bytes) {
        return new Part(title, kind, filename, type, bytes);
    }

    private static Part validPdf() {
        return file("CCNA certificate", "CERTIFICATE", "cert.pdf", "application/pdf", TestFiles.pdf());
    }

    /** The three documents every application must carry. */
    private static List<Part> requiredDocuments() {
        return List.of(
                file("ID document", "ID_DOCUMENT", "id.pdf", "application/pdf", TestFiles.pdf()),
                file("Matric certificate", "MATRIC_CERTIFICATE", "matric.pdf", "application/pdf",
                        TestFiles.pdf()),
                file("Proof of residence", "PROOF_OF_RESIDENCE", "residence.pdf",
                        "application/pdf", TestFiles.pdf()));
    }

    private static List<Part> allWith(Part... extra) {
        List<Part> all = new java.util.ArrayList<>(requiredDocuments());
        all.addAll(List.of(extra));
        return all;
    }

    /** Submits the three required documents followed by whatever extra parts the test gives. */
    private org.springframework.test.web.servlet.ResultActions submit(Applicant a, Part... parts)
            throws Exception {
        List<Part> all = new java.util.ArrayList<>(requiredDocuments());
        all.addAll(List.of(parts));
        return mvc.perform(multipartRequest(a, all));
    }

    private MockMultipartHttpServletRequestBuilder multipartRequest(Applicant a, List<Part> parts) {
        StringBuilder docs = new StringBuilder();
        for (Part p : parts) {
            if (docs.length() > 0) {
                docs.append(',');
            }
            docs.append("{\"kind\":\"%s\",\"title\":\"%s\"}".formatted(p.kind, p.title));
        }
        String json = a.toJson(docs.toString());

        MockMultipartHttpServletRequestBuilder builder = multipart("/v1/applications")
                .file(new MockMultipartFile("application", "", "application/json",
                        json.getBytes(StandardCharsets.UTF_8)));
        long length = json.length();
        for (Part p : parts) {
            builder.file(new MockMultipartFile("files", p.filename, p.contentType, p.bytes));
            length += p.bytes.length;
        }
        // The servlet filter refuses a submission that doesn't declare its size up front.
        // (Setting a body is what gives the mock request a known content length; the multipart
        // parts above are already parsed and don't read from it.)
        builder.content(new byte[(int) length + 2048]);
        return builder;
    }

    private org.springframework.test.web.servlet.ResultActions reject(
            String token, UUID id, String reason) throws Exception {
        return mvc.perform(post("/v1/admin/applications/" + id + "/reject")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"%s\"}".formatted(reason)));
    }

    private boolean exists(Applicant a) {
        return applications.findAll().stream().anyMatch(x -> x.getEmail().equals(a.email));
    }

    private TechnicianApplication find(Applicant a) {
        return applications.findAll().stream()
                .filter(x -> x.getEmail().equals(a.email))
                .findFirst().orElseThrow();
    }

    private User user(String email, UserRole role) {
        User user = new User();
        user.setEmail(email);
        user.setName("Test " + role);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        return users.save(user);
    }

    private String managerToken() throws Exception {
        return tokenFor(user("manager-" + UUID.randomUUID() + "@techconnect.test", UserRole.MANAGER));
    }

    private String tokenFor(User user) throws Exception {
        MockHttpServletRequestBuilder login = post("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(user.getEmail(), PASSWORD));
        String body = mvc.perform(login).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String marker = "\"token\":\"";
        int start = body.indexOf(marker) + marker.length();
        return body.substring(start, body.indexOf('"', start));
    }

    /** A valid applicant by default; tests overwrite one field to make it invalid. */
    private static final class Applicant {
        String firstName = "Nomsa";
        String lastName = "Khumalo";
        String email;
        String phone;
        String idNumber = SaIds.random();
        String experience = "Eight years installing and maintaining network and CCTV systems.";
        boolean consent = true;
        String website = "";
        /** Replaced by tests that apply from somewhere other than Durban. */
        String address = """
                {"street":"1 Test Street","suburb":"Umhlanga","city":"Durban",
                 "province":"KWAZULU_NATAL","postalCode":"4001"}""";

        static Applicant unique(String prefix) {
            Applicant a = new Applicant();
            a.email = prefix + "-" + UUID.randomUUID() + "@techconnect.test";
            // A 7-digit subscriber number, so the full number is +27 82 NNN NNNN (11 digits).
            long n = Math.abs(UUID.randomUUID().getMostSignificantBits() % 9_000_000L) + 1_000_000L;
            a.phone = "+27 82 %d %04d".formatted(n / 10_000, n % 10_000);
            return a;
        }

        String toJson(String documents) {
            return """
                    {"firstName":"%s","lastName":"%s","email":"%s","phone":"%s","idNumber":"%s",
                     "address":%s,
                     "experienceSummary":"%s","skills":"CCTV, structured cabling",
                     "documents":[%s],"consent":%s,"website":"%s"}"""
                    .formatted(firstName, lastName, email.replace("\r", "\\r").replace("\n", "\\n"),
                            phone, idNumber, address,
                            experience.replace("\u0000", "\\u0000").replace("\u001b", "\\u001b"),
                            documents, consent, website);
        }
    }
}
