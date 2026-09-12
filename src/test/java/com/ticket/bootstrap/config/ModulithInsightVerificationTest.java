package com.ticket.bootstrap.config;

import com.ticket.booking.OrderStarted;
import com.ticket.bootstrap.support.BookingE2ETestSupport;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.modulith.actuator.ApplicationModulesEndpoint;
import org.springframework.modulith.observability.support.ModuleObservabilityBeanPostProcessor;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * runtime insight/actuator 가시성을 확인한다.
 *
 * <p>{@code build.gradle}이 {@code testCompileOnly}로 관련 Modulith 아티팩트를 추가하는 이유(컴파일
 * 시점 타입 확인용이고 런타임 의존은 이미 {@code spring-modulith-starter-insight}가 끌어온다)는
 * {@code build.gradle} 주석 참고.
 *
 * <p>이 테스트가 확인하는 것은 두 가지다.
 * <ul>
 *   <li><b>module API trace가 등록된다</b>: {@link ModuleObservabilityBeanPostProcessor} 빈이
 *       context에 있으면 module 경계를 넘는 호출을 Micrometer Observation으로 계측하는 AOP
 *       instrumentation이 활성화된 것이다.</li>
 *   <li><b>event publication metric이 등록된다</b>: 실제 도메인 이벤트({@link OrderStarted})를
 *       발행하면 {@code module.events.published} 계열 counter가 {@link MeterRegistry}에 생긴다.
 *       이름은 구현 세부사항이라 접두어만 검사한다.</li>
 * </ul>
 *
 * <p>{@code /actuator/modulith}({@link ApplicationModulesEndpoint}, endpoint id {@code modulith})는
 * {@code management.endpoints.web.exposure.include}(application.yml: {@code health,info,prometheus})에
 * 없어 HTTP로 노출되지 않는다 — {@code ApiSecurityConfig}의 permitAll 목록에도 없어 나머지 API와 같은
 * {@code anyRequest().authenticated()}로 떨어진다. 그래서 인증 없이 호출하면 200이 아니라 401이다.
 * endpoint 빈 자체({@code @Endpoint} discovery 대상)는 노출 여부와 무관하게 여전히 만들어지므로
 * 그 존재도 함께 확인한다.
 */
@SuppressWarnings("NonAsciiCharacters")
class ModulithInsightVerificationTest extends BookingE2ETestSupport {

    @Autowired
    private ModuleObservabilityBeanPostProcessor moduleObservabilityBeanPostProcessor;

    @Autowired
    private ApplicationModulesEndpoint applicationModulesEndpoint;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void module_observability_bean_post_processor와_actuator_endpoint_빈이_등록된다() {
        assertThat(moduleObservabilityBeanPostProcessor).isNotNull();
        assertThat(applicationModulesEndpoint).isNotNull();
    }

    @Test
    void 도메인_이벤트를_발행하면_event_publication_metric이_등록된다() {
        eventPublisher.publishEvent(new OrderStarted(
                UUID.randomUUID(), OrderStarted.SCHEMA_VERSION, 999_999L, 1L,
                "insight-verification-hold", Set.of(1L), Instant.now()));

        assertThat(meterRegistry.getMeters())
                .as("module.events.published로 시작하는 counter가 최소 하나는 있어야 한다")
                .anyMatch(meter -> meter.getId().getName().startsWith("module.events.published"));
    }

    @Test
    void actuator_modulith는_인증_없이_접근할_수_없다() {
        final ResponseEntity<String> response = restTemplate.getForEntity("/actuator/modulith", String.class);

        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.OK);
        assertThat(response.getStatusCode().is2xxSuccessful()).isFalse();
    }
}
