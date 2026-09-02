package com.ticket.admission;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.test.ApplicationModuleTest;

/**
 * {@code verifyAutomatically = false}: 전체 애플리케이션 구조 검증({@code ApplicationModules.verify()})은
 * {@code com.ticket.ModularityTests}가 legacy package를 제외한 predicate로 이미 전담한다. 기본값(true)으로
 * 두면 이 STANDALONE 테스트가 별도로 {@code verify()}를 실행하는데, 아직 admission이 의존하는
 * {@code com.ticket.core.support.exception}이 legacy {@code com.ticket.core} 아래에 있어(모듈로 이동 전)
 * "admission → core"·"core → admission"(기존 use case가 admission 공개 API를 호출) 순환으로 오탐된다.
 * {@code spring.modulith.detection-strategy}를 전역으로 바꾸는 대신 이 테스트에서만 자동 검증을 꺼서,
 * 아직 {@code @ApplicationModule}을 붙이지 않은 미래 모듈이 조용히 검증에서 빠지는 위험을 피한다.
 */
@ApplicationModuleTest(verifyAutomatically = false)
class AdmissionModuleTests {

    @Test
    void bootstraps() {
    }
}
