package com.ticket.booking;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.admission.AdmissionVerifier;
import com.ticket.catalog.BookingPolicyLookup;
import com.ticket.catalog.ShowLookup;
import com.ticket.identity.AccessTokenAuthenticator;
import com.ticket.identity.MemberLookup;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * {@code verifyAutomatically = false}: 전체 애플리케이션 구조 검증({@code ApplicationModules.verify()})은
 * {@code com.ticket.ModularityTests}가 legacy package를 제외한 predicate로 이미 전담한다. 기본값(true)으로
 * 두면 이 STANDALONE 테스트가 별도로 {@code verify()}를 실행하는데, booking이 의존하는
 * {@code com.ticket.core.support.exception}이 legacy {@code com.ticket.core} 아래에 있어 "booking →
 * core" 순환으로 오탐된다. {@code spring.modulith.detection-strategy}를 전역으로 바꾸는 대신 이
 * 테스트에서만 자동 검증을 꺼서, 아직 {@code @ApplicationModule}을 붙이지 않은 미래 모듈이 조용히
 * 검증에서 빠지는 위험을 피한다.
 *
 * <p>STANDALONE bootstrap mode는 {@code com.ticket.booking} package tree만 component-scan한다.
 * catalog {@code BookingPolicyLookup}·{@code ShowLookup}, identity {@code MemberLookup}·
 * {@code AccessTokenAuthenticator}(WebSocket 인증이 참조), admission {@code AdmissionVerifier}는 그
 * 필터 밖이라 {@code @MockitoBean}으로 대체한다. {@code JPAQueryFactory}도
 * {@code @MockitoBean}으로 대체한다 — 이 테스트는 booking bean들이 module 경계 안에서 서로 정상
 * 배선되는지만 확인하는 wiring smoke test이지 실제 DB 접근을 검증하지 않는다. 그 검증은 각 Querydsl
 * repository의 통합 테스트가 담당한다. {@code Clock}은 booking이 실제로 참조하는 {@code shared}가
 * {@code @Modulith(sharedModules = "shared")} 덕에 이 테스트에도 자동 포함돼
 * {@code shared.internal.config.SystemClockConfig}가 진짜 bean을 채우므로 로컬 stub을 두지 않는다 —
 * 두면 이름이 겹쳐 {@code BeanDefinitionOverrideException}이 난다(실측 확인). {@code SimpMessagingTemplate}은
 * {@code WebSocketSeatStatusEventPublisher}(좌석 상태 WebSocket 발행)가 필요로 하는데, 이 bean을
 * 실제로 만드는 {@code @EnableWebSocketMessageBroker} 설정({@code com.ticket.config.internal.WebSocketConfig})은
 * 8번째 module인 {@code config}에 있어 STANDALONE 스캔 범위 밖이다 — 그래서 이것도 다른 외부
 * 의존과 같은 이유로 {@code @MockitoBean}으로 대체한다.
 */
@ApplicationModuleTest(verifyAutomatically = false)
class BookingModuleTests {

    @MockitoBean
    private BookingPolicyLookup bookingPolicyLookup;

    @MockitoBean
    private ShowLookup showLookup;

    @MockitoBean
    private MemberLookup memberLookup;

    @MockitoBean
    private AccessTokenAuthenticator accessTokenAuthenticator;

    @MockitoBean
    private AdmissionVerifier admissionVerifier;

    @MockitoBean
    private JPAQueryFactory jpaQueryFactory;

    @MockitoBean
    private SimpMessagingTemplate simpMessagingTemplate;

    @Test
    void bootstraps() {
    }
}
