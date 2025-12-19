package com.example.animegame.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(
                        "http://localhost",
                        "http://localhost:80",
                        "http://127.0.0.1"
                )
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}