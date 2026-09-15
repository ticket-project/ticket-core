package com.ticket.booking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.booking.domain.RequestedSeatIds;
import com.ticket.booking.domain.order.OrderRepository;
import com.ticket.booking.domain.order.OrderState;
import com.ticket.booking.domain.seat.PerformanceSeat;
import com.ticket.booking.domain.seat.PerformanceSeatRepository;
import com.ticket.booking.domain.seat.PerformanceSeatState;
import com.ticket.booking.exception.NoAvailableSeatException;
import com.ticket.booking.exception.PendingOrderAlreadyExistsException;
import com.ticket.booking.exception.SeatMismatchInPerformanceException;

/**
 * booking local DB만 보는 예매 가능 여부 판정을 고정한다.
 *
 * <p>옛 {@code PendingOrderLocalValidator}(진행 중인 PENDING 주문 확인)와 옛 {@code
 * HoldSeatAvailabilityValidator}(좌석 존재·판매 상태 확인)가 이 한 클래스로 합쳐졌다. 두 확인이 같은 읽기 전용 트랜잭션 안에서 이뤄져야 한다는
 * 것이 별도 bean으로 남은 유일한 이유이므로, 그 트랜잭션 계약도 여기서 함께 고정한다.
 */
@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class BookingAvailabilityCheckerTest {
    @Mock private OrderRepository orderRepository;
    @Mock private PerformanceSeatRepository performanceSeatRepository;
    @InjectMocks private BookingAvailabilityChecker checker;

    /** booking local 읽기는 짧은 읽기 전용 트랜잭션 하나로 묶인다 — 별도 bean으로 남은 이유가 이것뿐이다. */
    @Test
    void booking_local_읽기는_읽기_전용_트랜잭션에서_수행한다() throws NoSuchMethodException {
        final Transactional transactional =
                BookingAvailabilityChecker.class
                        .getDeclaredMethod("check", Long.class, Long.class, RequestedSeatIds.class)
                        .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }

    @Test
    void check는_requestedSeatIds를_직접_받는다() throws NoSuchMethodException {
        final Method method =
                BookingAvailabilityChecker.class.getDeclaredMethod(
                        "check", Long.class, Long.class, RequestedSeatIds.class);

        assertThat(method.getParameterTypes()[2]).isEqualTo(RequestedSeatIds.class);
    }

    @Test
    void 진행중인_pending_주문이_있으면_예외를_던지고_좌석을_조회하지_않는다() {
        final RequestedSeatIds seatIds = RequestedSeatIds.from(List.of(10L, 20L));
        when(orderRepository.existsByMemberIdAndPerformanceIdAndStatus(20L, 1L, OrderState.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> checker.check(20L, 1L, seatIds))
                .isInstanceOf(PendingOrderAlreadyExistsException.class)
                .hasFieldOrPropertyWithValue("memberId", 20L)
                .hasFieldOrPropertyWithValue("performanceId", 1L);

        verifyNoInteractions(performanceSeatRepository);
    }

    @Test
    void 좌석개수가_일치하지_않으면_seatMismatch예외를_던진다() {
        final PerformanceSeat availableSeat = mock(PerformanceSeat.class);
        final RequestedSeatIds seatIds = RequestedSeatIds.from(List.of(10L, 20L));
        when(performanceSeatRepository.findAllByPerformanceIdAndSeatIdIn(1L, List.of(10L, 20L)))
                .thenReturn(List.of(availableSeat));

        assertThatThrownBy(() -> checker.check(20L, 1L, seatIds))
                .isInstanceOf(SeatMismatchInPerformanceException.class)
                .hasFieldOrPropertyWithValue("performanceId", 1L);
    }

    @Test
    void 사용불가_좌석이_포함되면_notExistAvailableSeat예외를_던진다() {
        final PerformanceSeat availableSeat = createPerformanceSeat(PerformanceSeatState.AVAILABLE);
        final PerformanceSeat reservedSeat = createPerformanceSeat(PerformanceSeatState.RESERVED);
        final RequestedSeatIds seatIds = RequestedSeatIds.from(List.of(10L, 20L));
        when(performanceSeatRepository.findAllByPerformanceIdAndSeatIdIn(1L, List.of(10L, 20L)))
                .thenReturn(List.of(availableSeat, reservedSeat));

        assertThatThrownBy(() -> checker.check(20L, 1L, seatIds))
                .isInstanceOf(NoAvailableSeatException.class)
                .hasFieldOrPropertyWithValue("performanceId", 1L);
    }

    @Test
    void 모두_예매가능_좌석이면_그대로_반환한다() {
        final RequestedSeatIds seatIds = RequestedSeatIds.from(List.of(10L, 20L));
        final List<PerformanceSeat> seats =
                List.of(
                        createPerformanceSeat(PerformanceSeatState.AVAILABLE),
                        createPerformanceSeat(PerformanceSeatState.AVAILABLE));
        when(performanceSeatRepository.findAllByPerformanceIdAndSeatIdIn(1L, List.of(10L, 20L)))
                .thenReturn(seats);

        final List<PerformanceSeat> result = checker.check(20L, 1L, seatIds);

        assertThat(result).containsExactlyElementsOf(seats);
    }

    private PerformanceSeat createPerformanceSeat(final PerformanceSeatState state) {
        final PerformanceSeat performanceSeat = mock(PerformanceSeat.class);
        when(performanceSeat.getState()).thenReturn(state);
        return performanceSeat;
    }
}
