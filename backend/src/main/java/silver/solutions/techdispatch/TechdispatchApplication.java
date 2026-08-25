package silver.solutions.techdispatch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import silver.solutions.techdispatch.config.TechDispatchProperties;

@SpringBootApplication
@EnableConfigurationProperties(TechDispatchProperties.class)
public class TechdispatchApplication {

	public static void main(String[] args) {
		SpringApplication.run(TechdispatchApplication.class, args);
	}

}
