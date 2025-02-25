package com.note.application.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class CorsConfig {

	@Bean
	public CorsFilter corsFilter() {
		CorsConfiguration corsConfiguration = new CorsConfiguration();
		corsConfiguration.addAllowedOriginPattern("*");// Allow all origins
		corsConfiguration.addAllowedMethod("*"); // Allow all HTTP methods (GET, POST, etc.)
		corsConfiguration.addAllowedHeader("*");// Allow all headers
		corsConfiguration.setAllowCredentials(true);// Allow cookies and credentials

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", corsConfiguration);// Apply this to all endpoints

		return new CorsFilter(source);
	}
}
