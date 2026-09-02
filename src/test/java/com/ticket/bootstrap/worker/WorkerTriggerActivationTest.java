package com.ticket.bootstrap.worker;

import com.ticket.booking.internal.application.order.command.ExpirePendingOrdersUseCase;
import com.ticket.booking.internal.infrastructure.order.outbox.create.HoldCreationOutboxRelay;
import com.ticket.booking.internal.infrastructure.order.outbox.release.HoldReleaseOutboxRelay;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * worker.enabled로 background 트리거 전체를 끌 수 있는지 고정한다.
 */
@SuppressWarnings("NonAsciiCharacters")
class WorkerTriggerActivationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    CollaboratorStubs.class,
                    OrderExpirationTrigger.class,
                    HoldOutboxRelayTrigger.class
            );

    @Test
    void worker_설정이_없으면_트리거를_등록한다() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(OrderExpirationTrigger.class);
            assertThat(context).hasSingleBean(HoldOutboxRelayTrigger.class);
        });
    }

    @Test
    void worker_enabled가_false면_어떤_background_트리거도_등록되지_않는다() {
        contextRunner.withPropertyValues("worker.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(OrderExpirationTrigger.class);
            assertThat(context).doesNotHaveBean(HoldOutboxRelayTrigger.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class CollaboratorStubs {

        @Bean
        ExpirePendingOrdersUseCase expirePendingOrdersUseCase() {
            return mock(ExpirePendingOrdersUseCase.class);
        }

        @Bean
        HoldCreationOutboxRelay holdCreationOutboxRelay() {
            return mock(HoldCreationOutboxRelay.class);
        }

        @Bean
        HoldReleaseOutboxRelay holdReleaseOutboxRelay() {
            return mock(HoldReleaseOutboxRelay.class);
        }
    }
}
