package com.ticket.payment;

import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.ticket.testsupport.persistence.MigratedSchema;

/**
 * {@code verifyAutomatically = false}: 전체 애플리케이션 구조 검증은 {@code com.ticket.ModularityTests}가 이미 전담한다(다른
 * {@code *ModuleTests}와 같은 이유).
 */
@MigratedSchema
@ApplicationModuleTest(verifyAutomatically = false)
class PaymentModuleTests {
    @MockitoBean
    private RedissonClient redissonClient;

    @Test
    void bootstraps() {}
}
