package com.ticket.booking;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.admission.AdmissionVerifier;
import com.ticket.catalog.BookingPolicyLookup;
import com.ticket.catalog.PerformanceSaleCatalog;
import com.ticket.catalog.PerformanceVenueLayoutCatalog;
import com.ticket.catalog.ShowLookup;
import com.ticket.member.AccessTokenAuthenticator;
import com.ticket.member.MemberLookup;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;

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
 * catalog {@code BookingPolicyLookup}·{@code PerformanceSaleCatalog}·{@code PerformanceVenueLayoutCatalog}·
 * {@code ShowLookup}, member {@code MemberLookup}·
 * {@code AccessTokenAuthenticator}(WebSocket 인증이 참조), admission {@code AdmissionVerifier}는 그
 * 필터 밖이라 {@code @MockitoBean}으로 대체한다. {@code JPAQueryFactory}도
 * {@code @MockitoBean}으로 대체한다 — 이 테스트는 booking bean들이 module 경계 안에서 서로 정상
 * 배선되는지만 확인하는 wiring smoke test이지 실제 DB 접근을 검증하지 않는다. 그 검증은 각 Querydsl
 * repository의 통합 테스트가 담당한다. {@code Clock}도 같은 이유로 {@code @MockitoBean}이다 —
 * {@code shared}는 {@code @Modulith(sharedModules = "shared")} 덕에 이 테스트에 포함되지만 이제 호출
 * 대상 계약만 갖고 bean을 등록하지 않고, {@code Clock}을 만드는 {@code SystemClockConfig}는
 * {@code com.ticket.config}가 소유해 STANDALONE 스캔 범위 밖이다. {@code SimpMessagingTemplate}은
 * {@code WebSocketSeatStatusEventPublisher}(좌석 상태 WebSocket 발행)가 필요로 한다. 이 bean을
 * 만드는 {@code @EnableWebSocketMessageBroker} 설정
 * ({@code booking.internal.infrastructure.websocket.WebSocketConfig})은 이제 booking 소유라 스캔
 * 범위 안이지만, wiring smoke test에서 실제 STOMP 브로커 배선까지 띄울 이유가 없어 계속
 * {@code @MockitoBean}으로 대체한다({@code @MockitoBean}은 같은 타입의 실제 bean 정의를 대체한다).
 */
@ApplicationModuleTest(verifyAutomatically = false)
class BookingModuleTests {

    @MockitoBean
    private BookingPolicyLookup bookingPolicyLookup;

    @MockitoBean
    private PerformanceSaleCatalog performanceSaleCatalog;

    @MockitoBean
    private PerformanceVenueLayoutCatalog performanceVenueLayoutCatalog;

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

    @MockitoBean
    private Clock clock;

    @Test
    void bootstraps() {
    }
}
