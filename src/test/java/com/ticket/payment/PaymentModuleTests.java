package com.ticket.payment;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.test.ApplicationModuleTest;

/**
 * {@code verifyAutomatically = false}: 전체 애플리케이션 구조 검증은
 * {@code com.ticket.ModularityTests}가 legacy package를 제외한 predicate로 이미 전담한다(다른
 * {@code *ModuleTests}와 같은 이유, {@code AdmissionModuleTests} 참고).
 */
@ApplicationModuleTest(verifyAutomatically = false)
class PaymentModuleTests {

    @Test
    void bootstraps() {
    }
}
