package com.ticket.member;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.test.ApplicationModuleTest;

/**
 * {@code verifyAutomatically = false}: 전체 애플리케이션 구조 검증({@code ApplicationModules.verify()})은 {@code
 * com.ticket.ModularityTests}가 이미 전담한다. {@code spring.modulith.detection-strategy}를 전역으로 바꾸는 대신 이
 * 테스트에서만 자동 검증을 꺼서, 아직 {@code @ApplicationModule}을 붙이지 않은 미래 모듈이 조용히 검증에서 빠지는 위험을 피한다.
 *
 * <p>STANDALONE bootstrap mode는 {@code com.ticket.member} package tree만 component-scan한다.
 * JWT·OAuth2 provider·refresh token 저장이 security로 옮겨간 뒤로 member는 그 설정 없이 기동한다 — 이 테스트에 JWT secret이나
 * OAuth2 client 설정이 더 필요 없다는 사실 자체가 소유권 이동의 증거다.
 */
@ApplicationModuleTest(verifyAutomatically = false)
class MemberModuleTests {
    @Test
    void bootstraps() {}
}
