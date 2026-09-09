package com.ticket.booking.infrastructure;

import com.ticket.booking.application.AdmissionVerifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AdmissionTokenConfigurationProperties.class)
public class AdmissionTokenConfig {

    @Bean
    public AdmissionVerifier admissionVerifier(final AdmissionTokenConfigurationProperties properties) {
        return new JwtAdmissionVerifier(
                new AdmissionTokenSettings(
                        properties.getIssuer(),
                        properties.getAudience(),
                        properties.getSecretKey(),
                        properties.getExpirationSeconds()
                ),
                properties.isEnforcementEnabled()
        );
    }
}
