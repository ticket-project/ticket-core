package com.ticket.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** 감사자는 유일한 {@code AuditorAware} bean인 {@link SecurityContextAuditorAware}가 정한다. */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {}
