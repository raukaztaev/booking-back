package org.example.bookingback.config;

import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(defaultIfEmpty(
                properties.allowedOrigins(),
                List.of("http://localhost:3000", "http://localhost:3001")
        ));
        configuration.setAllowedMethods(defaultIfEmpty(
                properties.allowedMethods(),
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        ));
        configuration.setAllowedHeaders(defaultIfEmpty(properties.allowedHeaders(), List.of("*")));
        configuration.setAllowCredentials(properties.allowCredentials());
        configuration.setMaxAge(properties.maxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private List<String> defaultIfEmpty(List<String> configured, List<String> defaults) {
        return configured == null || configured.isEmpty() ? defaults : configured;
    }
}
