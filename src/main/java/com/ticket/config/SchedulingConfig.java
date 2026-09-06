package com.ticket.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * background worker(스케줄러)를 활성화한다.
 *
 * <p>{@code worker.enabled=false}면 이 설정이 등록되지 않아 어떤 {@code @Scheduled} 트리거도 실행되지 않는다.
 * API만 제공하는 인스턴스를 같은 실행 모듈로 띄울 때 쓴다.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "worker", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
