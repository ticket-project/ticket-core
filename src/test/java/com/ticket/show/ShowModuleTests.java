package com.ticket.show;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.member.MemberLookup;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;

/**
 * {@code verifyAutomatically = false}: 전체 애플리케이션 구조 검증({@code ApplicationModules.verify()})은
 * {@code com.ticket.ModularityTests}가 legacy package를 제외한 predicate로 이미 전담한다. 기본값(true)으로
 * 두면 이 STANDALONE 테스트가 별도로 {@code verify()}를 실행하는데, show가 의존하는
 * {@code com.ticket.core.support.exception}이 legacy {@code com.ticket.core} 아래에 있고, PerformanceSeat
 * 등 아직 이동하지 않은 legacy 코드가 이번에 옮긴 show entity(Performance, Seat, Show)를 그대로
 * 참조해 "show → core"·"core → show" 순환으로 오탐된다. {@code spring.modulith.detection-strategy}를
 * 전역으로 바꾸는 대신 이 테스트에서만 자동 검증을 꺼서, 아직 {@code @ApplicationModule}을 붙이지 않은
 * 미래 모듈이 조용히 검증에서 빠지는 위험을 피한다.
 *
 * <p>STANDALONE bootstrap mode는 {@code com.ticket.show} package tree만 component-scan한다.
 * {@code shared}는 {@code @Modulith(sharedModules = "shared")} 덕에 이 테스트에도 포함되지만 이제
 * 호출 대상 계약만 갖고 bean을 등록하지 않으므로(전역 기술 설정은 {@code com.ticket.config}가
 * 소유한다), 스캔 범위 밖에서 오는 {@code JPAQueryFactory}와 {@code Clock}은 {@code @MockitoBean}으로
 * 대체한다 — 이 테스트는 show bean들이 module 경계 안에서 서로 정상 배선되는지만 확인하는 wiring
 * smoke test이지 실제 DB 접근이나 시간 계산을 검증하지 않는다. 그 검증은 각 Querydsl repository의
 * 통합 테스트와 use case 테스트가 담당한다.
 *
 * <p>찜(showlike) 흡수로 show가 member의 {@link MemberLookup}을 참조하게 됐다 — 회원 존재
 * 확인용이다. member는 이 STANDALONE 스캔 범위 밖이라 마찬가지로 {@code @MockitoBean}으로
 * 대체한다.
 */
@ApplicationModuleTest(verifyAutomatically = false)
class ShowModuleTests {

    @MockitoBean
    private JPAQueryFactory jpaQueryFactory;

    @MockitoBean
    private Clock clock;

    @MockitoBean
    private MemberLookup memberLookup;

    @Test
    void bootstraps() {
    }
}
