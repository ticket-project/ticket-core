package com.ticket.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 시각처럼 테스트가 통제해야 하는 시스템 값을 빈으로 제공한다.
 *
 * <p>도메인 코드가 {@code LocalDateTime.now()}를 직접 부르면 판정 결과를 고정할 수 없다.
 * 주입 지점을 여기 한 곳으로 모은다.
 */
@Configuration
public class SystemClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }

}
