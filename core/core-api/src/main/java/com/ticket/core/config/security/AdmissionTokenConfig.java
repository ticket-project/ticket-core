package com.ticket.core.config.security;

import com.ticket.support.security.admission.AdmissionTokenProperties;
import com.ticket.support.security.admission.AdmissionTokenService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TicketAdmissionTokenProperties.class)
public class AdmissionTokenConfig {

    @Bean
    public AdmissionTokenService admissionTokenService(final TicketAdmissionTokenProperties properties) {
        return new AdmissionTokenService(new AdmissionTokenProperties(
                properties.getIssuer(),
                properties.getAudience(),
                properties.getSecretKey(),
                properties.getExpirationSeconds()
        ));
    }
}
