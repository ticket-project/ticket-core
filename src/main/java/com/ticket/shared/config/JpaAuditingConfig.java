package com.ticket.shared.config;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * 감사자는 유일한 {@code AuditorAware} bean인 {@link SecurityContextAuditorAware}가 정한다.
 *
 * <p>감사 시각은 도메인과 같은 {@link Clock} bean에서 읽는다. 기본 provider는 JVM 기본 시간대의 {@code LocalDateTime.now()}라, 운영 컨테이너(UTC)에서
 * {@code created_at}이 같은 순간의 {@code expires_at}(Asia/Seoul)보다 9시간 이르게 저장됐다.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {
    @Bean
    DateTimeProvider auditingDateTimeProvider(final Clock clock) {
        return () -> Optional.of(LocalDateTime.now(clock));
    }
}
