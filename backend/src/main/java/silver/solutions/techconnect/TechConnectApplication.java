package silver.solutions.techconnect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import silver.solutions.techconnect.config.TechConnectProperties;

@SpringBootApplication
@EnableConfigurationProperties(TechConnectProperties.class)
public class TechConnectApplication {

	public static void main(String[] args) {
		SpringApplication.run(TechConnectApplication.class, args);
	}

}
