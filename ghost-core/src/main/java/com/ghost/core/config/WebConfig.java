package com.ghost.core.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Pasta externa à build (não limpa no mvn clean)
        registry.addResourceHandler("/audio_cache/**")
                .addResourceLocations("file:./audio_cache/")
                .setCachePeriod(3600); // 1 hora de cache no browser
    }
}