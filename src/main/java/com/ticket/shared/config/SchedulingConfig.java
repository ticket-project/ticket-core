package com.ticket.shared.config;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;

/**
 * background worker(스케줄러)를 활성화한다.
 *
 * <p>{@code worker.enabled=false}면 이 설정이 등록되지 않아 어떤 {@code @Scheduled} 트리거도 실행되지 않는다. API만 제공하는 인스턴스를 같은 실행 모듈로 띄울 때
 * 쓴다.
 *
 * <p>{@code @SchedulerLock}을 붙인 작업은 인스턴스가 여러 대여도 한 대만 실행한다. 락은 DB 테이블({@code shedlock}, {@code __root} migration)에 두고,
 * 시각은 DB 시계로 잰다 — 인스턴스끼리 시계가 조금 달라도 락 만료 판단이 어긋나지 않는다.
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT1M")
@ConditionalOnProperty(prefix = "worker", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
    @Bean
    public LockProvider lockProvider(final DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }
}
