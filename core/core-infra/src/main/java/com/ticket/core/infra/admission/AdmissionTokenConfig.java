package com.ticket.core.infra.admission;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AdmissionTokenConfigurationProperties.class)
public class AdmissionTokenConfig {

    @Bean
    public JwtAdmissionGuard admissionTokenService(final AdmissionTokenConfigurationProperties properties) {
        return new JwtAdmissionGuard(
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
