package silver.solutions.techconnect.integration.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Which sender runs: real only with both settings, and a service account of the real shape. */
class PushSenderSelectionTest {

    @Configuration
    @EnableConfigurationProperties(PushProperties.class)
    @Import({FcmPushSender.class, DisabledPushSender.class})
    static class Senders {
    }

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(Senders.class);

    @Test
    void withNothingSetItStaysOff() {
        runner.run(context -> assertThat(context).hasSingleBean(DisabledPushSender.class));
    }

    @Test
    void aProjectWithoutAServiceAccountStaysOff() {
        runner.withPropertyValues("techconnect.push.project-id=tc-test")
                .run(context -> assertThat(context).hasSingleBean(DisabledPushSender.class));
    }

    @Test
    void withBothItUsesFirebaseEvenIfTheKeyHasAQuoteInIt() throws Exception {
        runner.withPropertyValues(
                        "techconnect.push.project-id=tc-test",
                        "techconnect.push.service-account-json=" + serviceAccount("Tech'Connect"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(FcmPushSender.class);
                });
    }

    @Test
    void aBrokenKeyStopsStartupRatherThanTheFirstDispatch() {
        runner.withPropertyValues(
                        "techconnect.push.project-id=tc-test",
                        "techconnect.push.service-account-json={\"type\":\"nonsense\"}")
                .run(context -> assertThat(context).hasFailed());
    }

    /** The JSON key file Firebase hands out, with a throwaway key. */
    private static String serviceAccount(String projectName) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        String key = Base64.getEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded());
        return """
                {"type":"service_account","project_id":"tc-test","private_key_id":"id","client_id":"1",\
                "client_email":"%s@tc-test.iam.gserviceaccount.com",\
                "private_key":"-----BEGIN PRIVATE KEY-----\\n%s\\n-----END PRIVATE KEY-----\\n"}"""
                .formatted(projectName, key);
    }
}
