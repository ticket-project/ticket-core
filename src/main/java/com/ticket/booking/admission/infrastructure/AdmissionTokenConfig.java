package com.ticket.booking.admission.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ticket.booking.admission.application.AdmissionVerifier;

@Configuration
@EnableConfigurationProperties(AdmissionTokenProperties.class)
public class AdmissionTokenConfig {
    @Bean
    public AdmissionVerifier admissionVerifier(final AdmissionTokenProperties properties) {
        return new JwtAdmissionVerifier(
                new AdmissionTokenSettings(
                        properties.getIssuer(),
                        properties.getAudience(),
                        properties.getSecretKey()),
                properties.isEnforcementEnabled());
    }
}
