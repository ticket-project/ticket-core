package com.ticket.core.infra.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ticket.core.domain.hold.command.HoldHistoryRecorder;
import com.ticket.core.domain.hold.command.HoldManager;
import com.ticket.core.domain.order.command.create.CreateOrderUseCase;
import com.ticket.core.domain.order.model.OrderState;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

class BookingMetricsAspectTest {

    @Test
    void records_order_create_success_and_failure() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        BookingMetricsAspect aspect = new BookingMetricsAspect(new CoreBookingMetrics(meterRegistry));
        CreateOrderUseCase target = mock(CreateOrderUseCase.class);
        CreateOrderUseCase proxy = proxy(target, aspect);
        CreateOrderUseCase.Input successfulInput = new CreateOrderUseCase.Input(1L, List.of(10L), 7L);
        CreateOrderUseCase.Input failingInput = new CreateOrderUseCase.Input(2L, List.of(20L), 8L);
        CreateOrderUseCase.Output output = new CreateOrderUseCase.Output(
                "order-key",
                OrderState.PENDING,
                LocalDateTime.of(2026, 8, 12, 12, 10)
        );
        when(target.execute(successfulInput)).thenReturn(output);
        when(target.execute(failingInput)).thenThrow(new IllegalStateException("db unavailable"));

        assertThat(proxy.execute(successfulInput)).isEqualTo(output);
        assertThatThrownBy(() -> proxy.execute(failingInput)).isInstanceOf(IllegalStateException.class);

        assertThat(counter(meterRegistry, "booking.order.create.success")).isEqualTo(1.0);
        assertThat(counter(meterRegistry, "booking.order.create.failure")).isEqualTo(1.0);
    }

    @Test
    void records_hold_create_release_and_expire_results() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        BookingMetricsAspect aspect = new BookingMetricsAspect(new CoreBookingMetrics(meterRegistry));
        HoldManager holdManager = proxy(mock(HoldManager.class), aspect);
        HoldHistoryRecorder historyRecorder = proxy(mock(HoldHistoryRecorder.class), aspect);

        holdManager.createHold(null, null, null, null, null);
        holdManager.release(null, null, null);
        historyRecorder.recordExpired(null, null, null, null, null);

        assertThat(counter(meterRegistry, "booking.hold.create", "result", "success")).isEqualTo(1.0);
        assertThat(counter(meterRegistry, "booking.hold.release", "result", "success")).isEqualTo(1.0);
        assertThat(counter(meterRegistry, "booking.hold.expire", "result", "success")).isEqualTo(1.0);
    }

    private <T> T proxy(final T target, final BookingMetricsAspect aspect) {
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAspect(aspect);
        return proxyFactory.getProxy();
    }

    private double counter(
            final SimpleMeterRegistry meterRegistry,
            final String name,
            final String... tags
    ) {
        return meterRegistry.get(name).tags(tags).counter().count();
    }
}
