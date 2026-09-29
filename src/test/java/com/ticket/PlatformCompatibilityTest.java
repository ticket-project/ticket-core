package com.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;

import com.ticket.testsupport.TestContainerImages;
import com.ticket.testsupport.persistence.MigratedSchema;

/**
 * H2(Oracle 모드)와 Testcontainers Redis로 실제에 가까운 인프라 위에서 애플리케이션 컨텍스트가 정상 기동하는지 확인하는 platform smoke test다. Modulith 구조 검증은
 * {@code com.ticket.ModularityTests}가 담당하므로 여기서는 다루지 않는다.
 */
@MigratedSchema
@SpringBootTest(classes = TicketApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@SuppressWarnings({"NonAsciiCharacters", "resource"})
class PlatformCompatibilityTest {
    private static final int REDIS_PORT = 6379;

    /** RedissonConfig가 기동 시점에 연결을 맺으므로 실제 Redis 없이는 컨텍스트가 뜨지 않는다. */
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(TestContainerImages.REDIS).withExposedPorts(REDIS_PORT);

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void redisProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
    }

    @Autowired
    private ApplicationContext context;

    @Test
    void boot_4_1_1과_modulith_2_1_1_조합으로_컨텍스트가_기동한다() {
        assertThat(context).isNotNull();
        assertThat(context.getBean(TicketApplication.class)).isNotNull();
    }
}
