package silver.solutions.techconnect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import silver.solutions.techconnect.config.TechConnectProperties;
import silver.solutions.techconnect.integration.freshdesk.FreshdeskProperties;
import silver.solutions.techconnect.integration.push.PushProperties;

@SpringBootApplication
@EnableConfigurationProperties({TechConnectProperties.class, FreshdeskProperties.class, PushProperties.class})
public class TechConnectApplication {

	public static void main(String[] args) {
		SpringApplication.run(TechConnectApplication.class, args);
	}

}
