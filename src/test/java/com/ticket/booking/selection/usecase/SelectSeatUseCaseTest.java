package com.ticket.booking.selection.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ticket.booking.admission.AdmissionVerifier;
import com.ticket.booking.admission.BookingEntryGuard;
import com.ticket.booking.exception.AdmissionTokenRequiredException;
import com.ticket.booking.exception.NoAvailableSeatException;
import com.ticket.booking.exception.PerformanceIsPastException;
import com.ticket.booking.exception.SeatAlreadyHeldException;
import com.ticket.booking.exception.SeatMismatchInPerformanceException;
import com.ticket.booking.hold.domain.HoldRegistry;
import com.ticket.booking.salespolicy.domain.BookingWindow;
import com.ticket.booking.salespolicy.domain.HoldPolicy;
import com.ticket.booking.salespolicy.domain.PerformanceSalesPolicy;
import com.ticket.booking.salespolicy.domain.PerformanceSalesPolicyRepository;
import com.ticket.booking.salespolicy.domain.QueueMode;
import com.ticket.booking.salespolicy.domain.QueuePolicy;
import com.ticket.booking.salespolicy.usecase.PerformanceSaleFinder;
import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.booking.seat.domain.PerformanceSeatRepository;
import com.ticket.booking.seat.domain.PerformanceSeatState;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class SelectSeatUseCaseTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-04T01:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    private static final SelectSeatUseCase.Input INPUT = new SelectSeatUseCase.Input(10L, 20L, 1L, "admission-token");

    @Mock
    private PerformanceSalesPolicyRepository performanceSalesPolicyRepository;

    @Mock
    private SeatSelectionWriter seatSelectionWriter;

    @Mock
    private PerformanceSeatRepository performanceSeatRepository;

    @Mock
    private HoldRegistry holdRegistry;

    @Mock
    private AdmissionVerifier admissionVerifier;

    private SelectSeatUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase =
                // 실제 collaborator를 mock repository/verifier 위에 씌운다 -- 그래야 "없으면 404"와
                // "대기열이 필요할 때만 검증"이라는 분기가 mock에 가려지지 않고 그대로 검증된다.
                new SelectSeatUseCase(
                        seatSelectionWriter,
                        performanceSeatRepository,
                        holdRegistry,
                        new BookingEntryGuard(
                                new PerformanceSaleFinder(performanceSalesPolicyRepository), admissionVerifier),
                        CLOCK);
    }

    @Test
    void 정책_판정_좌석_검증_선택_순서로_수행한다() {
        PerformanceSalesPolicy policy = openPolicy(false);
        when(performanceSalesPolicyRepository.findById(10L)).thenReturn(Optional.of(policy));
        when(performanceSeatRepository.findSeatState(10L, 20L)).thenReturn(Optional.of(availableSeat()));

        useCase.execute(INPUT);

        InOrder inOrder =
                inOrder(performanceSalesPolicyRepository, performanceSeatRepository, holdRegistry, seatSelectionWriter);
        inOrder.verify(performanceSalesPolicyRepository).findById(10L);
        inOrder.verify(performanceSeatRepository).findSeatState(10L, 20L);
        inOrder.verify(holdRegistry).isHeld(10L, 20L);
        // 검증에서 얻은 performanceSeatId와 회차 선점 한도를 넘긴다. SELECTED 발행은 SeatSelectionWriter가 좌석 락
        // 안에서 하므로 여기서 다시 발행하지 않는다.
        inOrder.verify(seatSelectionWriter)
                .select(10L, 20L, 1L, 501L, policy.getBookingWindow().getClosesAt(), 4);
    }

    @Test
    void 대기열이_필요없는_회차는_입장_검사를_하지_않는다() {
        when(performanceSalesPolicyRepository.findById(10L)).thenReturn(Optional.of(openPolicy(false)));
        when(performanceSeatRepository.findSeatState(10L, 20L)).thenReturn(Optional.of(availableSeat()));

        useCase.execute(INPUT);

        verify(admissionVerifier, never()).verify(10L, 1L, "admission-token");
    }

    @Test
    void 대기열이_필요한_회차는_좌석_조회_전에_입장을_검사한다() {
        when(performanceSalesPolicyRepository.findById(10L)).thenReturn(Optional.of(openPolicy(true)));
        doThrow(new AdmissionTokenRequiredException()).when(admissionVerifier).verify(10L, 1L, "admission-token");

        assertThatThrownBy(() -> useCase.execute(INPUT)).isInstanceOf(AdmissionTokenRequiredException.class);

        verifyNoInteractions(performanceSeatRepository, holdRegistry, seatSelectionWriter);
    }

    @Test
    void 예매가_마감된_회차는_좌석을_조회하지_않는다() {
        when(performanceSalesPolicyRepository.findById(10L))
                .thenReturn(Optional.of(policy(NOW.minusHours(2), NOW.minusHours(1), false)));

        assertThatThrownBy(() -> useCase.execute(INPUT)).isInstanceOf(PerformanceIsPastException.class);

        verifyNoInteractions(performanceSeatRepository, holdRegistry, seatSelectionWriter, admissionVerifier);
    }

    @Test
    void 회차에_없는_좌석이면_선택하지_않는다() {
        openPerformance();
        when(performanceSeatRepository.findSeatState(10L, 20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(INPUT)).isInstanceOf(SeatMismatchInPerformanceException.class);

        verifyNoInteractions(holdRegistry, seatSelectionWriter);
    }

    @Test
    void 이미_판매된_좌석이면_hold를_보지_않고_실패한다() {
        openPerformance();
        when(performanceSeatRepository.findSeatState(10L, 20L))
                .thenReturn(Optional.of(seat(PerformanceSeatState.RESERVED)));

        assertThatThrownBy(() -> useCase.execute(INPUT)).isInstanceOf(NoAvailableSeatException.class);

        verifyNoInteractions(holdRegistry, seatSelectionWriter);
    }

    /** 락 밖의 사전 확인이다 — 락 안에서 SeatSelectionWriter가 다시 보는 것과 같은 검증이 아니다. */
    @Test
    void 이미_선점된_좌석이면_선택하지_않는다() {
        openPerformance();
        when(performanceSeatRepository.findSeatState(10L, 20L)).thenReturn(Optional.of(availableSeat()));
        when(holdRegistry.isHeld(10L, 20L)).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(INPUT)).isInstanceOf(SeatAlreadyHeldException.class);

        verifyNoInteractions(seatSelectionWriter);
    }

    private void openPerformance() {
        when(performanceSalesPolicyRepository.findById(10L)).thenReturn(Optional.of(openPolicy(false)));
    }

    private PerformanceSeat availableSeat() {
        return seat(PerformanceSeatState.AVAILABLE);
    }

    private PerformanceSeat seat(final PerformanceSeatState state) {
        final PerformanceSeat seat = new PerformanceSeat(10L, 20L, 1L, state, BigDecimal.ZERO);
        ReflectionTestUtils.setField(seat, "id", 501L);
        return seat;
    }

    private PerformanceSalesPolicy openPolicy(final boolean queueRequired) {
        return policy(NOW.minusHours(1), NOW.plusHours(1), queueRequired);
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
