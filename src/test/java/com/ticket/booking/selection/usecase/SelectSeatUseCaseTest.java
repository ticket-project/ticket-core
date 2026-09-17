package com.ticket.booking.selection.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticket.booking.application.AdmissionGuard;
import com.ticket.booking.application.PerformanceSaleFinder;
import com.ticket.booking.application.port.AdmissionVerifier;
import com.ticket.booking.domain.salespolicy.BookingEntryPolicy;
import com.ticket.booking.domain.salespolicy.HoldPolicy;
import com.ticket.booking.domain.salespolicy.OrderAcceptanceWindow;
import com.ticket.booking.domain.salespolicy.PerformanceSalesPolicy;
import com.ticket.booking.domain.salespolicy.PerformanceSalesPolicyRepository;
import com.ticket.booking.domain.salespolicy.QueueMode;
import com.ticket.booking.domain.seat.PerformanceSeatRepository;
import com.ticket.booking.domain.seat.PerformanceSeatState;
import com.ticket.booking.domain.seat.PerformanceSeatStateSnapshot;
import com.ticket.booking.exception.AdmissionTokenRequiredException;
import com.ticket.booking.exception.NoAvailableSeatException;
import com.ticket.booking.exception.PerformanceIsPastException;
import com.ticket.booking.exception.SeatAlreadyHeldException;
import com.ticket.booking.exception.SeatMismatchInPerformanceException;
import com.ticket.booking.hold.domain.HoldManager;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class SelectSeatUseCaseTest {
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-04T01:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    private static final SelectSeatUseCase.Input INPUT =
            new SelectSeatUseCase.Input(10L, 20L, 1L, "admission-token");
    @Mock private PerformanceSalesPolicyRepository performanceSalesPolicyRepository;
    @Mock private SeatSelectionCoordinator seatSelectionCoordinator;
    @Mock private PerformanceSeatRepository performanceSeatRepository;
    @Mock private HoldManager holdManager;
    @Mock private AdmissionVerifier admissionVerifier;
    private SelectSeatUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase =
                // 실제 collaborator를 mock repository/verifier 위에 씌운다 -- 그래야 "없으면 404"와
                // "대기열이 필요할 때만 검증"이라는 분기가 mock에 가려지지 않고 그대로 검증된다.
                new SelectSeatUseCase(
                        new PerformanceSaleFinder(performanceSalesPolicyRepository),
                        seatSelectionCoordinator,
                        performanceSeatRepository,
                        holdManager,
                        new AdmissionGuard(admissionVerifier),
                        CLOCK);
    }

    @Test
    void 정책_판정_좌석_검증_선택_순서로_수행한다() {
        PerformanceSalesPolicy policy = openPolicy(false);
        when(performanceSalesPolicyRepository.findById(10L)).thenReturn(Optional.of(policy));
        when(performanceSeatRepository.findSeatState(10L, 20L))
                .thenReturn(Optional.of(availableSeat()));

        useCase.execute(INPUT);

        InOrder inOrder =
                inOrder(
                        performanceSalesPolicyRepository,
                        performanceSeatRepository,
                        holdManager,
                        seatSelectionCoordinator);
        inOrder.verify(performanceSalesPolicyRepository).findById(10L);
        inOrder.verify(performanceSeatRepository).findSeatState(10L, 20L);
        inOrder.verify(holdManager).isHeld(10L, 20L);
        // 검증에서 얻은 performanceSeatId를 그대로 넘긴다. SELECTED 발행은 coordinator가 좌석 락
        // 안에서 하므로 여기서 다시 발행하지 않는다.
        inOrder.verify(seatSelectionCoordinator)
                .select(10L, 20L, 1L, 501L, policy.getOrderAcceptanceWindow().getClosesAt());
    }

    @Test
    void 대기열이_필요없는_회차는_입장_검사를_하지_않는다() {
        when(performanceSalesPolicyRepository.findById(10L))
                .thenReturn(Optional.of(openPolicy(false)));
        when(performanceSeatRepository.findSeatState(10L, 20L))
                .thenReturn(Optional.of(availableSeat()));

        useCase.execute(INPUT);

        verify(admissionVerifier, never()).verify(10L, 1L, "admission-token");
    }

    @Test
    void 대기열이_필요한_회차는_좌석_조회_전에_입장을_검사한다() {
        when(performanceSalesPolicyRepository.findById(10L))
                .thenReturn(Optional.of(openPolicy(true)));
        doThrow(new AdmissionTokenRequiredException())
                .when(admissionVerifier)
                .verify(10L, 1L, "admission-token");

        assertThatThrownBy(() -> useCase.execute(INPUT))
                .isInstanceOf(AdmissionTokenRequiredException.class);

        verifyNoInteractions(performanceSeatRepository, holdManager, seatSelectionCoordinator);
    }

    @Test
    void 예매가_마감된_회차는_좌석을_조회하지_않는다() {
        when(performanceSalesPolicyRepository.findById(10L))
                .thenReturn(Optional.of(policy(NOW.minusHours(2), NOW.minusHours(1), false)));

        assertThatThrownBy(() -> useCase.execute(INPUT))
                .isInstanceOf(PerformanceIsPastException.class);

        verifyNoInteractions(
                performanceSeatRepository,
                holdManager,
                seatSelectionCoordinator,
                admissionVerifier);
    }

    @Test
    void 회차에_없는_좌석이면_선택하지_않는다() {
        openPerformance();
        when(performanceSeatRepository.findSeatState(10L, 20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(INPUT))
                .isInstanceOf(SeatMismatchInPerformanceException.class)
                .hasFieldOrPropertyWithValue("performanceId", 10L);

        verifyNoInteractions(holdManager, seatSelectionCoordinator);
    }

    @Test
    void 이미_판매된_좌석이면_hold를_보지_않고_실패한다() {
        openPerformance();
        when(performanceSeatRepository.findSeatState(10L, 20L))
                .thenReturn(
                        Optional.of(
                                new PerformanceSeatStateSnapshot(
                                        501L, PerformanceSeatState.RESERVED)));

        assertThatThrownBy(() -> useCase.execute(INPUT))
                .isInstanceOf(NoAvailableSeatException.class)
                .hasFieldOrPropertyWithValue("performanceId", 10L);

        verifyNoInteractions(holdManager, seatSelectionCoordinator);
    }

    /** 락 밖의 사전 확인이다 — 락 안에서 coordinator가 다시 보는 것과 같은 검증이 아니다. */
    @Test
    void 이미_선점된_좌석이면_선택하지_않는다() {
        openPerformance();
        when(performanceSeatRepository.findSeatState(10L, 20L))
                .thenReturn(Optional.of(availableSeat()));
        when(holdManager.isHeld(10L, 20L)).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(INPUT))
                .isInstanceOf(SeatAlreadyHeldException.class)
                .hasFieldOrPropertyWithValue("performanceId", 10L)
                .hasFieldOrPropertyWithValue("seatId", 20L);

        verifyNoInteractions(seatSelectionCoordinator);
    }

    private void openPerformance() {
        when(performanceSalesPolicyRepository.findById(10L))
                .thenReturn(Optional.of(openPolicy(false)));
    }

    private PerformanceSeatStateSnapshot availableSeat() {
        return new PerformanceSeatStateSnapshot(501L, PerformanceSeatState.AVAILABLE);
    }

    private PerformanceSalesPolicy openPolicy(final boolean queueRequired) {
        return policy(NOW.minusHours(1), NOW.plusHours(1), queueRequired);
    }

    private PerformanceSalesPolicy policy(
            final LocalDateTime orderOpenTime,
            final LocalDateTime orderCloseTime,
            final boolean queueRequired) {
        return new PerformanceSalesPolicy(
                10L,
                new OrderAcceptanceWindow(orderOpenTime, orderCloseTime),
                new HoldPolicy(4, Duration.ofSeconds(300)),
                queueRequired
                        ? new BookingEntryPolicy(QueueMode.FORCE_ON, null, null, null, null)
                        : BookingEntryPolicy.none());
    }
}
