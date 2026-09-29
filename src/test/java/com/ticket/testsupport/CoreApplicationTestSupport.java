package com.ticket.testsupport;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;

import com.ticket.TicketApplication;
import com.ticket.testsupport.persistence.MigratedSchema;

/**
 * 실제 애플리케이션 전체({@link TicketApplication})를 띄우는 테스트의 베이스다. H2는 {@link MigratedSchema}가, Redis는 여기 있는 Testcontainers가
 * 맡는다. 기동에 필요한 환경변수 값은 {@code src/test/resources/config/application.yml}에 있다.
 *
 * <p>설정을 여기 한 곳에 두어야 상속한 테스트들이 Spring context 하나를 재사용한다. 하위 클래스가 속성·bean을 더하면 그 조합마다 context가 따로 뜬다.
 */
@MigratedSchema
@SpringBootTest(classes = TicketApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@SuppressWarnings("resource")
public abstract class CoreApplicationTestSupport {
    private static final int REDIS_PORT = 6379;

    /**
     * JVM 하나에 컨테이너 하나를 쓴다. @Testcontainers의 @Container는 테스트 클래스마다 컨테이너를 띄우고 클래스가 끝나면 멈추는데, Spring 컨텍스트는 클래스 사이에 재사용된다.
     * 그러면 두 번째 테스트 클래스가 이미 멈춘 컨테이너의 포트를 가리킨 컨텍스트를 그대로 물려받아 실패한다. 정리는 Testcontainers의 Ryuk이 JVM 종료 시 맡는다.
     *
     * <p>RedissonConfig가 기동 시점에 연결을 맺으므로 실제 Redis 없이는 컨텍스트가 뜨지 않는다.
     */
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
}
