package com.ticket.like;

import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.member.api.MemberLookupApi;
import com.ticket.testsupport.persistence.MigratedSchema;

/**
 * {@code verifyAutomatically = false}: 전체 애플리케이션 구조 검증은 {@code com.ticket.ModularityTests}가 전담한다(다른 module 테스트와 같은 이유).
 *
 * <p>STANDALONE bootstrap mode는 {@code com.ticket.like} package tree만 component-scan한다. {@code shared}는
 * {@code @Modulith(sharedModules = "shared")} 덕에 이 테스트에도 포함되므로 {@code com.ticket.shared.config}의
 * {@code JPAQueryFactory} bean도 함께 등록되지만, wiring smoke test라 실제 DB 접근이 필요 없어 {@code @MockitoBean}으로 대체한다. like는 대상 존재도
 * 요청 회원의 활성 상태도 확인하지 않아(package-info 참고) {@code member}의 공개 계약 {@link MemberLookupApi}를 부르지 않는다. 이 mock은 쓰는 bean이 없어 기동
 * 결과에 영향을 주지 않는다.
 */
@MigratedSchema
@ApplicationModuleTest(verifyAutomatically = false)
class LikeModuleTests {
    @MockitoBean
    private RedissonClient redissonClient;

    @MockitoBean
    private JPAQueryFactory jpaQueryFactory;

    @MockitoBean
    private MemberLookupApi memberLookupApi;

    @Test
    void bootstraps() {}
}
