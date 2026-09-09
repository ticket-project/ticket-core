package com.ticket.like;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.member.MemberLookup;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * {@code verifyAutomatically = false}: 전체 애플리케이션 구조 검증은 {@code com.ticket.ModularityTests}가
 * 전담한다(다른 module 테스트와 같은 이유).
 *
 * <p>STANDALONE bootstrap mode는 {@code com.ticket.like} package tree만 component-scan한다.
 * {@code shared}는 {@code @Modulith(sharedModules = "shared")} 덕에 이 테스트에도 포함되지만 호출
 * 대상 계약만 갖고 bean을 등록하지 않으므로, 스캔 범위 밖에서 오는 {@code JPAQueryFactory}는
 * {@code @MockitoBean}으로 대체한다. like는 찜하기/찜 해제/찜 상태 조회의 회원 활성 확인을 위해
 * {@code member}의 공개 계약 {@link MemberLookup}을 직접 부르므로(대상 존재는 확인하지 않는다 —
 * package-info 참고) 이것도 같은 이유로 mock한다.
 */
@ApplicationModuleTest(verifyAutomatically = false)
class LikeModuleTests {

    @MockitoBean
    private JPAQueryFactory jpaQueryFactory;

    @MockitoBean
    private MemberLookup memberLookup;

    @Test
    void bootstraps() {
    }
}
