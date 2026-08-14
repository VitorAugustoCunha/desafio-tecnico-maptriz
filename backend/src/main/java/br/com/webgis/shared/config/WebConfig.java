package br.com.webgis.shared.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	private static final Logger log = LoggerFactory.getLogger(WebConfig.class);

	private final CorsProperties cors;

	public WebConfig(CorsProperties cors) {
		this.cors = cors;
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		List<String> origens = cors.origensPermitidas();

		if (origens.isEmpty()) {
			log.info("CORS desabilitado: nenhuma origem externa configurada (frontend servido na mesma origem).");
			return;
		}

		log.info("CORS habilitado para as origens: {}", origens);

		registry.addMapping("/api/**")
				.allowedOrigins(origens.toArray(String[]::new))
				.allowedMethods("GET", "POST", "PUT", "DELETE")
				.allowedHeaders("Content-Type", "Accept")
				.exposedHeaders("Location")
				.maxAge(3600);
	}
}
