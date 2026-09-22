package com.ticket.security;

import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.ticket.member.api.MemberAccountApi;

/**
 * security가 STANDALONE으로 부트스트랩되는지만 본다. member의 공개 계약은 {@code @MockitoBean}으로 대체한다 — 인증 조립이 member 내부가 아니라 이 계약에만 기대는지가
 * 이 테스트로 드러난다.
 *
 * <p>access token 읽기 계약은 더 이상 mock으로 대체하지 않는다. JWT 구현이 이 module 안({@code security.jwt})으로 들어와, 계약을 mock으로 바꾸면 같은
 * module의 발급기가 쓰는 실제 codec까지 사라진다.
 */
@ApplicationModuleTest(verifyAutomatically = false)
@TestPropertySource(
        properties = {
            "app.cors.allowed-origins=http://localhost:3000",
            "JWT_SECRET=0123456789abcdef0123456789abcdef",
            "JWT_ACCESS_TOKEN_EXPIRATION_SECONDS=1800",
            "JWT_REFRESH_TOKEN_EXPIRATION_SECONDS=1209600",
            "GOOGLE_CLIENT_ID=security-module-test",
            "GOOGLE_CLIENT_SECRET=security-module-test",
            "KAKAO_CLIENT_ID=security-module-test",
            "KAKAO_CLIENT_SECRET=security-module-test",
            "KAKAO_ADMIN_KEY=security-module-test",
            "OAUTH2_SUCCESS_REDIRECT_URI=http://localhost:3000/auth/callback",
            "OAUTH2_FAILURE_REDIRECT_URI=http://localhost:3000/auth/callback"
        })
class SecurityModuleTests {
    @MockitoBean
    private MemberAccountApi memberAccountOperations;

    @MockitoBean
    private RedissonClient redissonClient;

    @Test
    void bootstraps() {}
}
