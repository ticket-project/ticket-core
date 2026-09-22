package com.ticket.shared.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;

@Getter
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {
    private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:3000"));

    public void setAllowedOrigins(final List<String> allowedOrigins) {
        this.allowedOrigins =
                allowedOrigins == null ? new ArrayList<>() : new ArrayList<>(allowedOrigins);
    }
}
