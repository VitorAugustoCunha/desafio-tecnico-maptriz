package br.com.webgis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class WebGisApplication {

	public static void main(String[] args) {
		SpringApplication.run(WebGisApplication.class, args);
	}
}
