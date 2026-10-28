package com.ticket.booking;

import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.member.api.MemberLookupApi;
import com.ticket.security.api.AccessTokenAuthenticationApi;
import com.ticket.show.api.PerformanceLayoutApi;
import com.ticket.show.api.PerformanceSaleInfoApi;
import com.ticket.show.api.PerformanceSummaryApi;
import com.ticket.testsupport.persistence.MigratedSchema;

/**
 * {@code verifyAutomatically = false}: 전체 애플리케이션 구조 검증({@code ApplicationModules.verify()})은
 * {@code com.ticket.ModularityTests}가 이미 전담한다. {@code spring.modulith.detection-strategy}를 전역으로 바꾸는 대신 이 테스트에서만 자동 검증을
 * 꺼서, 아직 {@code @ApplicationModule}을 붙이지 않은 미래 모듈이 조용히 검증에서 빠지는 위험을 피한다.
 *
 * <p>STANDALONE bootstrap mode는 {@code com.ticket.booking} package tree만 component-scan한다. show
 * {@code PerformanceSaleInfoApi}·{@code PerformanceLayoutApi}·{@code PerformanceSummaryApi}(예매 정책은 booking의
 * {@code PerformanceSalesPolicy}가 소유하므로 show의 정책 공개 계약은 없다), member {@code MemberLookupApi}·
 * {@code AccessTokenAuthenticationApi}(WebSocket 인증이 참조)는 그 필터 밖이라 {@code @MockitoBean}으로 대체한다. {@code shared}는
 * {@code @Modulith(sharedModules = "shared")} 덕에 이 테스트에도 포함되므로 {@code com.ticket.shared.config}의
 * {@code JPAQueryFactory}·{@code Clock} bean도 함께 등록되지만, 둘 다 {@code @MockitoBean}으로 대체한다({@code @MockitoBean}은 같은 타입의 실제
 * bean 정의를 대체한다) — 이 테스트는 booking bean들이 module 경계 안에서 서로 정상 연결되는지만 확인하는 wiring smoke test이지 실제 DB 접근이나 시간 계산을 검증하지
 * 않는다. {@code SimpMessagingTemplate}은 {@code SeatStatusRelay}(좌석 상태 relay를 받아 WebSocket으로 보냄)가 필요로 한다. 이 bean을 만드는
 * {@code @EnableWebSocketMessageBroker} 설정({@code booking.websocket.WebSocketConfig})은 booking 소유라 스캔 범위 안이지만, wiring
 * smoke test에서 실제 STOMP 브로커 설정까지 띄울 이유가 없어 {@code @MockitoBean}으로 대체한다.
 */
@MigratedSchema
@ApplicationModuleTest(verifyAutomatically = false)
class BookingModuleTests {
    @MockitoBean
    private RedissonClient redissonClient;

    /** 시작하면 Redis에 좌석 상태 relay 구독 연결을 맺는다. wiring smoke test라 실제 구독은 필요 없다. */
    @MockitoBean
    private RedisMessageListenerContainer seatStatusRelayListenerContainer;

    @MockitoBean
    private PerformanceSaleInfoApi performanceSaleInfoApi;

    @MockitoBean
    private PerformanceLayoutApi performanceLayoutApi;

    @MockitoBean
    private PerformanceSummaryApi performanceSummaryApi;

    @MockitoBean
    private MemberLookupApi memberLookupApi;

    @MockitoBean
    private AccessTokenAuthenticationApi accessTokenAuthenticationApi;

    @MockitoBean
    private JPAQueryFactory jpaQueryFactory;

    @MockitoBean
    private SimpMessagingTemplate simpMessagingTemplate;

    @MockitoBean
    private Clock clock;

    @Test
    void bootstraps() {}
}
