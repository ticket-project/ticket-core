package com.ticket.bootstrap;

import com.ticket.TicketApplication;
import com.ticket.core.app.event.IntegrationEventPublisher;
import com.ticket.core.app.lock.LockManager;
import com.ticket.core.app.order.command.CreateOrderUseCase;
import com.ticket.core.app.order.command.ExpirePendingOrdersUseCase;
import com.ticket.core.infra.order.outbox.create.HoldCreationOutboxRelay;
import com.ticket.core.infra.order.outbox.release.HoldReleaseOutboxRelay;
import com.ticket.bootstrap.worker.HoldOutboxRelayTrigger;
import com.ticket.bootstrap.worker.OrderExpirationTrigger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실행 모듈이 전체 컨텍스트를 실제로 조립하는지 확인한다.
 *
 * <p>단위 테스트는 각 클래스를 직접 생성하므로 빈 배선이 깨져도 통과한다. 모듈 사이로 빈을 옮기는
 * 변경에서 기동 실패를 잡아내려면 컨텍스트를 한 번은 통째로 띄워봐야 한다.
 */
@Testcontainers
@SpringBootTest(
        classes = TicketApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:context-load;MODE=Oracle;DB_CLOSE_DELAY=-1",
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
                "GOOGLE_CLIENT_ID=context-load",
                "GOOGLE_CLIENT_SECRET=context-load",
                "KAKAO_CLIENT_ID=context-load",
                "KAKAO_CLIENT_SECRET=context-load",
                "KAKAO_ADMIN_KEY=context-load",
                "OAUTH2_SUCCESS_REDIRECT_URI=http://localhost:3000/auth/callback",
                "OAUTH2_FAILURE_REDIRECT_URI=http://localhost:3000/auth/callback"
        }
)
@SuppressWarnings({"NonAsciiCharacters", "resource"})
class ApplicationContextLoadTest {

    private static final int REDIS_PORT = 6379;

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(REDIS_PORT);

    @DynamicPropertySource
    static void redisProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
    }

    @Autowired
    private ApplicationContext context;

    @Test
    void 실행_모듈이_네_모듈을_한_컨텍스트로_조립한다() {
        assertThat(context.getBean(CreateOrderUseCase.class)).isNotNull();
        assertThat(beanOf("com.ticket.core.domain.order.repository.OrderRepository")).isNotNull();
        assertThat(context.getBean(LockManager.class)).isNotNull();
        assertThat(context.getBean(IntegrationEventPublisher.class)).isNotNull();
        assertThat(context.getBean(HoldCreationOutboxRelay.class)).isNotNull();
        assertThat(context.getBean(HoldReleaseOutboxRelay.class)).isNotNull();
    }

    /**
     * 도메인 Repository는 포트이고 실제 빈은 infra 어댑터다. 어댑터가 빠지면 기동에서 바로 드러난다.
     */
    @Test
    void 도메인_Repository는_infra_어댑터로_구현된다() {
        assertThat(beanOf("com.ticket.core.domain.order.repository.OrderRepository").getClass().getName())
                .startsWith("com.ticket.core.infra.");
        assertThat(beanOf("com.ticket.core.domain.hold.store.HoldStore").getClass().getName())
                .startsWith("com.ticket.core.infra.");
        assertThat(context.getBean(LockManager.class).getClass().getName())
                .startsWith("com.ticket.core.infra.");
    }

    /**
     * bootstrap은 도메인을 컴파일 타임에 보지 않는다. 런타임 클래스패스에만 있으므로 이름으로 찾는다.
     */
    private Object beanOf(final String typeName) {
        try {
            return context.getBean(Class.forName(typeName));
        } catch (final ClassNotFoundException exception) {
            throw new IllegalStateException("런타임 클래스패스에 없습니다: " + typeName, exception);
        }
    }

    @Test
    void worker가_켜져_있으면_background_트리거가_등록된다() {
        assertThat(context.getBeansOfType(OrderExpirationTrigger.class)).hasSize(1);
        assertThat(context.getBeansOfType(HoldOutboxRelayTrigger.class)).hasSize(1);
        assertThat(context.getBean(ExpirePendingOrdersUseCase.class)).isNotNull();
    }
}
