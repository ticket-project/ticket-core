package com.ticket.security;

import com.ticket.member.AccessTokenReader;
import org.redisson.api.RedissonClient;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ApplicationModuleTest(verifyAutomatically = false)
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:3000")
class SecurityModuleTests {

    @MockitoBean
    private AccessTokenReader accessTokenReader;

    @MockitoBean
    private RedissonClient redissonClient;

    @Test
    void bootstraps() {
    }
}
