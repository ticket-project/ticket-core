package com.ticket.booking.order.usecase;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.booking.exception.NoAvailableSeatException;
import com.ticket.booking.exception.PendingOrderAlreadyExistsException;
import com.ticket.booking.exception.SeatMismatchInPerformanceException;
import com.ticket.booking.order.domain.OrderRepository;
import com.ticket.booking.order.domain.OrderState;
import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.booking.seat.domain.PerformanceSeatRepository;
import com.ticket.booking.seat.domain.PerformanceSeatState;

import lombok.RequiredArgsConstructor;

/**
 * 예매를 시작해도 되는지를 booking 자신의 DB만 보고 판단하고, 통과하면 그 좌석들을 돌려준다.
 *
 * <p><b>별도 bean인 이유는 트랜잭션 경계 하나뿐이다.</b> 여기 있는 두 확인은 같은 읽기 트랜잭션 안에서 이뤄져야 하고, 그 트랜잭션은 다른 module 호출이나 Redis 왕복을 포함하지 않을 만큼
 * 짧아야 한다. {@link com.ticket.booking.order.usecase.StartBookingUseCase}가 같은 클래스의 private method로 호출하면 Spring proxy가 적용되지
 * 않아 트랜잭션이 아예 걸리지 않는다.
 *
 * <p>확인하는 것은 둘이다.
 *
 * <ul>
 *   <li>같은 회원·회차에 이미 진행 중인 PENDING 주문이 있는가 — 있으면 새 예매를 시작하지 않는다
 *   <li>요청한 좌석이 모두 이 회차의 좌석이고 아직 판매 가능한가
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class BookingAvailabilityChecker {
    private final OrderRepository orderRepository;
    private final PerformanceSeatRepository performanceSeatRepository;

    /** @return 요청한 좌석에 해당하는 {@code PerformanceSeat} 목록. 뒤 단계가 단가·등급을 다시 조회하지 않도록 함께 돌려준다 */
    @Transactional(readOnly = true)
    public List<PerformanceSeat> check(
            final Long memberId, final Long performanceId, final RequestedSeatIds requestedSeatIds) {
        ensureNoPendingOrder(memberId, performanceId);
        return requireAvailableSeats(performanceId, requestedSeatIds);
    }

    private void ensureNoPendingOrder(final Long memberId, final Long performanceId) {
        if (orderRepository.existsByMemberIdAndPerformanceIdAndStatus(memberId, performanceId, OrderState.PENDING)) {
            throw new PendingOrderAlreadyExistsException(memberId, performanceId);
        }
    }

    private List<PerformanceSeat> requireAvailableSeats(
            final Long performanceId, final RequestedSeatIds requestedSeatIds) {
        final List<PerformanceSeat> performanceSeats =
                performanceSeatRepository.findAllByPerformanceIdAndSeatIdIn(performanceId, requestedSeatIds.toList());
        if (performanceSeats.size() != requestedSeatIds.size()) {
            throw new SeatMismatchInPerformanceException(performanceId);
        }

        final boolean hasUnavailableSeat =
                performanceSeats.stream().anyMatch(seat -> seat.getState() != PerformanceSeatState.AVAILABLE);
        if (hasUnavailableSeat) {
            throw new NoAvailableSeatException(performanceId);
        }
        return performanceSeats;
    }
}
