package com.ticket.security;

import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.ticket.member.api.MemberAccountApi;
import com.ticket.testsupport.persistence.MigratedSchema;

/**
 * security가 STANDALONE으로 부트스트랩되는지만 본다. member의 공개 계약은 {@code @MockitoBean}으로 대체한다 — 인증 조립이 member 내부가 아니라 이 계약에만 기대는지가
 * 이 테스트로 드러난다.
 *
 * <p>access token 읽기 계약은 더 이상 mock으로 대체하지 않는다. JWT 구현이 이 module 안({@code security.jwt})으로 들어와, 계약을 mock으로 바꾸면 같은
 * module의 발급기가 쓰는 실제 codec까지 사라진다.
 */
@MigratedSchema
@ApplicationModuleTest(verifyAutomatically = false)
@TestPropertySource(properties = {"app.cors.allowed-origins=http://localhost:3000"})
class SecurityModuleTests {
    @MockitoBean
    private MemberAccountApi memberAccountApi;

    @MockitoBean
    private RedissonClient redissonClient;

    @Test
    void bootstraps() {}
}
