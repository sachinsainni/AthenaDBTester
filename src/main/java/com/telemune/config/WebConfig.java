package com.telemune.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

/**
 * Configures CORS (for external frontends) and serves the static UI
 * from src/main/resources/static/ at the root path.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/athena/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/");
    }
}
