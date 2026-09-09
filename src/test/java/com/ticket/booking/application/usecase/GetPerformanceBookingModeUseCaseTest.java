package com.ticket.booking.application.usecase;

import com.ticket.booking.domain.BookingEntryPolicy;
import com.ticket.booking.domain.HoldPolicy;
import com.ticket.booking.domain.OrderAcceptanceStatus;
import com.ticket.booking.domain.OrderAcceptanceWindow;
import com.ticket.booking.domain.PerformanceSalesPolicy;
import com.ticket.booking.domain.QueueMode;
import com.ticket.booking.domain.PerformanceSalesPolicyRepository;
import com.ticket.error.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class GetPerformanceBookingModeUseCaseTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-04T01:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @Mock
    private PerformanceSalesPolicyRepository performanceSalesPolicyRepository;

    private GetPerformanceBookingModeUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetPerformanceBookingModeUseCase(performanceSalesPolicyRepository, CLOCK);
    }

    @Test
    void 접수기간이고_대기열이_필요없으면_DIRECT다() {
        when(performanceSalesPolicyRepository.findById(10L))
                .thenReturn(Optional.of(policy(NOW.minusHours(1), NOW.plusHours(1), false)));

        GetPerformanceBookingModeUseCase.Output output =
                useCase.execute(new GetPerformanceBookingModeUseCase.Input(10L));

        assertThat(output.acceptanceStatus()).isEqualTo(OrderAcceptanceStatus.OPEN);
        assertThat(output.bookingMode()).isEqualTo(GetPerformanceBookingModeUseCase.BookingMode.DIRECT);
    }

    @Test
    void 접수기간이고_대기열이_필요하면_QUEUE다() {
        when(performanceSalesPolicyRepository.findById(10L))
                .thenReturn(Optional.of(policy(NOW.minusHours(1), NOW.plusHours(1), true)));

        GetPerformanceBookingModeUseCase.Output output =
                useCase.execute(new GetPerformanceBookingModeUseCase.Input(10L));

        assertThat(output.bookingMode()).isEqualTo(GetPerformanceBookingModeUseCase.BookingMode.QUEUE);
    }

    @Test
    void 접수_시작_전이면_UNAVAILABLE이다() {
        when(performanceSalesPolicyRepository.findById(10L))
                .thenReturn(Optional.of(policy(NOW.plusHours(1), NOW.plusHours(2), false)));

        GetPerformanceBookingModeUseCase.Output output =
                useCase.execute(new GetPerformanceBookingModeUseCase.Input(10L));

        assertThat(output.acceptanceStatus()).isEqualTo(OrderAcceptanceStatus.BEFORE_OPEN);
        assertThat(output.bookingMode()).isEqualTo(GetPerformanceBookingModeUseCase.BookingMode.UNAVAILABLE);
    }

    @Test
    void 접수_마감_후면_UNAVAILABLE이다() {
        when(performanceSalesPolicyRepository.findById(10L))
                .thenReturn(Optional.of(policy(NOW.minusHours(2), NOW.minusHours(1), false)));

        GetPerformanceBookingModeUseCase.Output output =
                useCase.execute(new GetPerformanceBookingModeUseCase.Input(10L));

        assertThat(output.acceptanceStatus()).isEqualTo(OrderAcceptanceStatus.CLOSED);
        assertThat(output.bookingMode()).isEqualTo(GetPerformanceBookingModeUseCase.BookingMode.UNAVAILABLE);
    }

    @Test
    void 판매_정책이_없으면_예외를_던진다() {
        when(performanceSalesPolicyRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new GetPerformanceBookingModeUseCase.Input(10L)))
                .isInstanceOf(NotFoundException.class);
    }

    private PerformanceSalesPolicy policy(
            final LocalDateTime orderOpenTime,
            final LocalDateTime orderCloseTime,
            final boolean queueRequired
    ) {
        return new PerformanceSalesPolicy(
                10L,
                new OrderAcceptanceWindow(orderOpenTime, orderCloseTime),
                new HoldPolicy(4, Duration.ofSeconds(300)),
                queueRequired
                        ? new BookingEntryPolicy(QueueMode.FORCE_ON, null, null, null, null)
                        : BookingEntryPolicy.none()
        );
    }
}
