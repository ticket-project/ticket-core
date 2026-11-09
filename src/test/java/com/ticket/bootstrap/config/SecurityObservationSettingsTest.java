package com.ticket.bootstrap.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ticket.bootstrap.support.BookingE2ETestSupport;

import io.micrometer.core.instrument.MeterRegistry;

/** Spring Security 계측은 꺼져 있고, gatling 판정 도구가 읽는 요청 시간 계측({@code http.server.requests})은 남는지 본다. */
@SuppressWarnings("NonAsciiCharacters")
class SecurityObservationSettingsTest extends BookingE2ETestSupport {
    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void HTTP_요청_뒤_요청_시간_계측은_남고_보안_계측은_없다() {
        // 인증이 필요한 주소도 불러 인증·권한 확인 경로를 지나게 한다.
        restTemplate.getForEntity("/actuator/health", String.class);
        restTemplate.getForEntity("/api/v1/members/me", String.class);

        assertThat(meterRegistry.find("http.server.requests").timers()).isNotEmpty();
        assertThat(meterRegistry.getMeters())
                .noneMatch(meter -> meter.getId().getName().startsWith("spring.security"));
    }
}
