package com.ticket.booking.salespolicy.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticket.booking.salespolicy.domain.BookingWindow;
import com.ticket.booking.salespolicy.domain.BookingWindowStatus;
import com.ticket.booking.salespolicy.domain.HoldPolicy;
import com.ticket.booking.salespolicy.domain.PerformanceSalesPolicy;
import com.ticket.booking.salespolicy.domain.PerformanceSalesPolicyRepository;
import com.ticket.booking.salespolicy.domain.QueueMode;
import com.ticket.booking.salespolicy.domain.QueuePolicy;
import com.ticket.shared.exception.NotFoundException;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class GetPerformanceEntryModeUseCaseTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-04T01:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @Mock
    private PerformanceSalesPolicyRepository performanceSalesPolicyRepository;

    private GetPerformanceEntryModeUseCase useCase;

    @BeforeEach
    void setUp() {
        // 실제 collaborator를 mock repository/verifier 위에 씌운다 -- 그래야 "없으면 404"와
        // "대기열이 필요할 때만 검증"이라는 분기가 mock에 가려지지 않고 그대로 검증된다.
        useCase =
                new GetPerformanceEntryModeUseCase(new PerformanceSaleFinder(performanceSalesPolicyRepository), CLOCK);
    }

    @Test
    void 접수기간이고_대기열이_필요없으면_DIRECT다() {
        when(performanceSalesPolicyRepository.findById(10L))
                .thenReturn(Optional.of(policy(NOW.minusHours(1), NOW.plusHours(1), false)));

        GetPerformanceEntryModeUseCase.Output output = useCase.execute(new GetPerformanceEntryModeUseCase.Input(10L));

        assertThat(output.acceptanceStatus()).isEqualTo(BookingWindowStatus.OPEN);
        assertThat(output.bookingMode()).isEqualTo(GetPerformanceEntryModeUseCase.EntryMode.DIRECT);
    }

    @Test
    void 접수기간이고_대기열이_필요하면_QUEUE다() {
        when(performanceSalesPolicyRepository.findById(10L))
                .thenReturn(Optional.of(policy(NOW.minusHours(1), NOW.plusHours(1), true)));

        GetPerformanceEntryModeUseCase.Output output = useCase.execute(new GetPerformanceEntryModeUseCase.Input(10L));

        assertThat(output.bookingMode()).isEqualTo(GetPerformanceEntryModeUseCase.EntryMode.QUEUE);
    }

    @Test
    void 접수_시작_전이면_UNAVAILABLE이다() {
        when(performanceSalesPolicyRepository.findById(10L))
                .thenReturn(Optional.of(policy(NOW.plusHours(1), NOW.plusHours(2), false)));

        GetPerformanceEntryModeUseCase.Output output = useCase.execute(new GetPerformanceEntryModeUseCase.Input(10L));

        assertThat(output.acceptanceStatus()).isEqualTo(BookingWindowStatus.BEFORE_OPEN);
        assertThat(output.bookingMode()).isEqualTo(GetPerformanceEntryModeUseCase.EntryMode.UNAVAILABLE);
    }

    @Test
    void 접수_마감_후면_UNAVAILABLE이다() {
        when(performanceSalesPolicyRepository.findById(10L))
                .thenReturn(Optional.of(policy(NOW.minusHours(2), NOW.minusHours(1), false)));

        GetPerformanceEntryModeUseCase.Output output = useCase.execute(new GetPerformanceEntryModeUseCase.Input(10L));

        assertThat(output.acceptanceStatus()).isEqualTo(BookingWindowStatus.CLOSED);
        assertThat(output.bookingMode()).isEqualTo(GetPerformanceEntryModeUseCase.EntryMode.UNAVAILABLE);
    }

    @Test
    void 판매_정책이_없으면_예외를_던진다() {
        when(performanceSalesPolicyRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new GetPerformanceEntryModeUseCase.Input(10L)))
                .isInstanceOf(NotFoundException.class);
    }

    private PerformanceSalesPolicy policy(
            final LocalDateTime orderOpenTime, final LocalDateTime orderCloseTime, final boolean queueRequired) {
        return new PerformanceSalesPolicy(
                10L,
                new BookingWindow(orderOpenTime, orderCloseTime),
                new HoldPolicy(4, Duration.ofSeconds(300)),
                queueRequired ? new QueuePolicy(QueueMode.FORCE_ON, null) : QueuePolicy.none());
    }
}
