package com.ticket;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 1 platform smoke test다.
 *
 * <p>Spring Boot 4.1.1과 Spring Modulith 2.1.1 조합이 기존 security/JPA/Redis 설정으로 컨텍스트를
 * 기동할 수 있는지만 확인한다. 아직 멀티프로젝트 구조이고 패키지 이동은 하지 않았으므로 여기서는
 * Modulith 구조 검증({@code ApplicationModules.verify()})을 다루지 않는다. 그 검증은 단일 Gradle
 * 프로젝트로 합치는 후속 Task에서 추가한다.
 */
@SpringBootTest(
        classes = TicketApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:platform-compat;MODE=Oracle;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.flyway.enabled=false",
                "app.seed.enabled=false",
                "app.seed.load-test-fixture.enabled=false",
                "JWT_SECRET=0123456789abcdef0123456789abcdef",
                "JWT_ACCESS_TOKEN_EXPIRATION_SECONDS=1800",
                "JWT_REFRESH_TOKEN_EXPIRATION_SECONDS=1209600",
                "GOOGLE_CLIENT_ID=platform-compat",
                "GOOGLE_CLIENT_SECRET=platform-compat",
                "KAKAO_CLIENT_ID=platform-compat",
                "KAKAO_CLIENT_SECRET=platform-compat",
                "KAKAO_ADMIN_KEY=platform-compat",
                "OAUTH2_SUCCESS_REDIRECT_URI=http://localhost:3000/auth/callback",
                "OAUTH2_FAILURE_REDIRECT_URI=http://localhost:3000/auth/callback"
        }
)
@SuppressWarnings({"NonAsciiCharacters", "resource"})
class PlatformCompatibilityTest {

    private static final int REDIS_PORT = 6379;

    /** RedissonConfig가 기동 시점에 연결을 맺으므로 실제 Redis 없이는 컨텍스트가 뜨지 않는다. */
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(REDIS_PORT);

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
