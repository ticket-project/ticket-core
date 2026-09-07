package com.ticket.venue;

import com.querydsl.jpa.impl.JPAQueryFactory;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * {@code verifyAutomatically = false}: 전체 애플리케이션 구조 검증은 {@code com.ticket.ModularityTests}가
 * 전담한다(다른 module 테스트와 같은 이유).
 *
 * <p>STANDALONE bootstrap mode는 {@code com.ticket.venue} package tree만 component-scan한다.
 * {@code shared}는 {@code @Modulith(sharedModules = "shared")} 덕에 이 테스트에도 포함되지만 호출
 * 대상 계약만 갖고 bean을 등록하지 않으므로, 스캔 범위 밖에서 오는 {@code JPAQueryFactory}는
 * {@code @MockitoBean}으로 대체한다. venue는 업무 module을 하나도 참조하지 않는 leaf(payment와 같은
 * 형태)라 다른 module의 공개 계약을 mock할 필요가 없다.
 */
@ApplicationModuleTest(verifyAutomatically = false)
class VenueModuleTests {

    @MockitoBean
    private JPAQueryFactory jpaQueryFactory;

    @Test
    void bootstraps() {
    }
}
