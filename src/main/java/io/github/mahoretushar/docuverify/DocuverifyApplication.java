package io.github.mahoretushar.docuverify;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DocuverifyApplication {

	public static void main(String[] args) {
		SpringApplication.run(DocuverifyApplication.class, args);
	}
}
