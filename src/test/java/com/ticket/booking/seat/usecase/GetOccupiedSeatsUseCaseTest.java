package com.ticket.booking.seat.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticket.booking.admission.AdmissionVerifier;
import com.ticket.booking.admission.BookingEntryGuard;
import com.ticket.booking.exception.AdmissionErrorCode;
import com.ticket.booking.exception.AdmissionTokenException;
import com.ticket.booking.exception.BookingErrorCode;
import com.ticket.booking.exception.BookingException;
import com.ticket.booking.hold.domain.HoldStore;
import com.ticket.booking.salespolicy.domain.BookingWindow;
import com.ticket.booking.salespolicy.domain.HoldPolicy;
import com.ticket.booking.salespolicy.domain.PerformanceSalesPolicy;
import com.ticket.booking.salespolicy.domain.PerformanceSalesPolicyRepository;
import com.ticket.booking.salespolicy.domain.QueueMode;
import com.ticket.booking.salespolicy.domain.QueuePolicy;
import com.ticket.booking.salespolicy.usecase.PerformanceSaleFinder;
import com.ticket.booking.seat.domain.PerformanceSeatRepository;
import com.ticket.booking.seat.domain.SeatOccupancy;
import com.ticket.booking.selection.domain.SeatSelectionService;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class GetOccupiedSeatsUseCaseTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-04T01:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @Mock
    private PerformanceSalesPolicyRepository performanceSalesPolicyRepository;

    @Mock
    private PerformanceSeatRepository performanceSeatRepository;

    @Mock
    private SeatSelectionService seatSelectionService;

    @Mock
    private HoldStore holdStore;

    @Mock
    private AdmissionVerifier admissionVerifier;

    private GetOccupiedSeatsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetOccupiedSeatsUseCase(
                performanceSeatRepository,
                new SeatOccupancy(seatSelectionService, holdStore),
                new BookingEntryGuard(new PerformanceSaleFinder(performanceSalesPolicyRepository), admissionVerifier),
                CLOCK);
    }

    @Test
    void 판매_확정_좌석과_redis_선택_선점_좌석을_합쳐_seatId_오름차순으로_한_번씩_돌려준다() {
        when(performanceSalesPolicyRepository.findById(10L)).thenReturn(Optional.of(openPolicy()));
        when(performanceSeatRepository.findReservedSeatIds(10L)).thenReturn(List.of(7L, 2L));
        when(seatSelectionService.getSelectingSeatIds(10L)).thenReturn(Set.of(5L, 2L));
        when(holdStore.getHoldingSeatIds(10L)).thenReturn(Set.of(9L));

        GetOccupiedSeatsUseCase.Output output =
                useCase.execute(new GetOccupiedSeatsUseCase.Input(10L, 100L, "admission-token"));

        assertThat(output.occupiedSeatIds()).containsExactly(2L, 5L, 7L, 9L);
    }

    /** 점유 목록은 표시용이라 1초 재사용하지만, 입장권·판매 기간 확인은 요청마다 한다. */
    @Test
    void 일초_안에_다시_부르면_점유_목록은_다시_읽지_않고_진입_검사는_매번_한다() {
        when(performanceSalesPolicyRepository.findById(10L)).thenReturn(Optional.of(openPolicy()));
        when(performanceSeatRepository.findReservedSeatIds(10L)).thenReturn(List.of(7L));
        when(seatSelectionService.getSelectingSeatIds(10L)).thenReturn(Set.of(5L));
        when(holdStore.getHoldingSeatIds(10L)).thenReturn(Set.of());

        useCase.execute(new GetOccupiedSeatsUseCase.Input(10L, 100L, "admission-token"));
        GetOccupiedSeatsUseCase.Output second =
                useCase.execute(new GetOccupiedSeatsUseCase.Input(10L, 200L, "other-token"));

        assertThat(second.occupiedSeatIds()).containsExactly(5L, 7L);
        verify(performanceSalesPolicyRepository, times(2)).findById(10L);
        verify(performanceSeatRepository, times(1)).findReservedSeatIds(10L);
        verify(seatSelectionService, times(1)).getSelectingSeatIds(10L);
        verify(holdStore, times(1)).getHoldingSeatIds(10L);
    }

    @Test
    void 일초가_지나면_점유_목록을_다시_읽는다() {
        final MutableClock clock = new MutableClock(CLOCK.instant(), CLOCK.getZone());
        useCase = new GetOccupiedSeatsUseCase(
                performanceSeatRepository,
                new SeatOccupancy(seatSelectionService, holdStore),
                new BookingEntryGuard(new PerformanceSaleFinder(performanceSalesPolicyRepository), admissionVerifier),
                clock);
        when(performanceSalesPolicyRepository.findById(10L)).thenReturn(Optional.of(openPolicy()));
        when(performanceSeatRepository.findReservedSeatIds(10L)).thenReturn(List.of());
        when(seatSelectionService.getSelectingSeatIds(10L)).thenReturn(Set.of(5L), Set.of(5L, 6L));
        when(holdStore.getHoldingSeatIds(10L)).thenReturn(Set.of());

        useCase.execute(new GetOccupiedSeatsUseCase.Input(10L, 100L, "admission-token"));
        clock.advance(GetOccupiedSeatsUseCase.SNAPSHOT_TTL);

        assertThat(useCase.execute(new GetOccupiedSeatsUseCase.Input(10L, 100L, "admission-token"))
                        .occupiedSeatIds())
                .containsExactly(5L, 6L);
        verify(seatSelectionService, times(2)).getSelectingSeatIds(10L);
    }

    @Test
    void 점유된_좌석이_없으면_빈_목록이다() {
        when(performanceSalesPolicyRepository.findById(10L)).thenReturn(Optional.of(openPolicy()));
        when(performanceSeatRepository.findReservedSeatIds(10L)).thenReturn(List.of());
        when(seatSelectionService.getSelectingSeatIds(10L)).thenReturn(Set.of());
        when(holdStore.getHoldingSeatIds(10L)).thenReturn(Set.of());

        assertThat(useCase.execute(new GetOccupiedSeatsUseCase.Input(10L, 100L, "admission-token"))
                        .occupiedSeatIds())
                .isEmpty();
    }

    @Test
    void 예매가_마감된_회차는_좌석을_읽지_않는다() {
        when(performanceSalesPolicyRepository.findById(10L))
                .thenReturn(Optional.of(policy(NOW.minusHours(2), NOW.minusHours(1), false)));

        assertThatThrownBy(() -> useCase.execute(new GetOccupiedSeatsUseCase.Input(10L, 100L, "admission-token")))
                .isInstanceOf(BookingException.class)
                .hasFieldOrPropertyWithValue("errorCode", BookingErrorCode.E3001);

        verifyNoInteractions(performanceSeatRepository, seatSelectionService, holdStore);
    }

    @Test
    void 대기열이_필요한_회차는_좌석을_읽기_전에_입장을_검사한다() {
        when(performanceSalesPolicyRepository.findById(10L))
                .thenReturn(Optional.of(policy(NOW.minusHours(1), NOW.plusHours(1), true)));
        doThrow(new AdmissionTokenException(AdmissionErrorCode.E8000, "admission token missing"))
                .when(admissionVerifier)
                .verify(10L, 100L, "admission-token");

        assertThatThrownBy(() -> useCase.execute(new GetOccupiedSeatsUseCase.Input(10L, 100L, "admission-token")))
                .isInstanceOf(AdmissionTokenException.class)
                .hasFieldOrPropertyWithValue("errorCode", AdmissionErrorCode.E8000);

        verifyNoInteractions(performanceSeatRepository, seatSelectionService, holdStore);
    }

    private PerformanceSalesPolicy openPolicy() {
        return policy(NOW.minusHours(1), NOW.plusHours(1), false);
    }

    private PerformanceSalesPolicy policy(
            final LocalDateTime orderOpenTime, final LocalDateTime orderCloseTime, final boolean queueRequired) {
        return new PerformanceSalesPolicy(
                10L,
                new BookingWindow(orderOpenTime, orderCloseTime),
                new HoldPolicy(4, Duration.ofSeconds(300)),
                queueRequired ? new QueuePolicy(QueueMode.FORCE_ON, null) : QueuePolicy.none());
    }

    /** 점유 목록 재사용 시간이 지나는 것을 보려고 테스트에서만 시간을 앞으로 민다. */
    private static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        private MutableClock(final Instant instant, final ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        private void advance(final Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(final ZoneId newZone) {
            return new MutableClock(instant, newZone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
