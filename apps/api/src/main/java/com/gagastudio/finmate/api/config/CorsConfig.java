package com.gagastudio.finmate.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
    private static final String[] LOCAL_FRONTEND_ORIGINS = {
            "http://localhost:3000",
            "http://localhost:5173",
            "http://127.0.0.1:3000",
            "http://127.0.0.1:5173"
    };

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        register(registry, "/api/**");
        register(registry, "/health");
    }

    private void register(CorsRegistry registry, String pathPattern) {
        registry.addMapping(pathPattern)
                .allowedOrigins(LOCAL_FRONTEND_ORIGINS)
                .allowedMethods("GET", "POST", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
