package com.ticket.member;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.TestPropertySource;

/**
 * {@code verifyAutomatically = false}: 전체 애플리케이션 구조 검증({@code ApplicationModules.verify()})은
 * {@code com.ticket.ModularityTests}가 이미 전담한다. {@code spring.modulith.detection-strategy}를
 * 전역으로 바꾸는 대신 이 테스트에서만 자동 검증을 꺼서, 아직 {@code @ApplicationModule}을 붙이지
 * 않은 미래 모듈이 조용히 검증에서 빠지는 위험을 피한다.
 *
 * <p>STANDALONE bootstrap mode는 {@code com.ticket.member} package tree만 component-scan한다.
 * member 자체가 UUID 공급 빈을 제공하므로 Redis 기반 인증 어댑터도 같은 모듈 안에서 기동한다.
 */
@ApplicationModuleTest(verifyAutomatically = false)
@TestPropertySource(properties = {
        "JWT_SECRET=0123456789abcdef0123456789abcdef",
        "JWT_ACCESS_TOKEN_EXPIRATION_SECONDS=1800",
        "JWT_REFRESH_TOKEN_EXPIRATION_SECONDS=1209600",
        "GOOGLE_CLIENT_ID=member-module-test",
        "GOOGLE_CLIENT_SECRET=member-module-test",
        "KAKAO_CLIENT_ID=member-module-test",
        "KAKAO_CLIENT_SECRET=member-module-test",
        "KAKAO_ADMIN_KEY=member-module-test",
        "OAUTH2_SUCCESS_REDIRECT_URI=http://localhost:3000/auth/callback",
        "OAUTH2_FAILURE_REDIRECT_URI=http://localhost:3000/auth/callback"
})
class MemberModuleTests {

    @Test
    void bootstraps() {
    }

}
