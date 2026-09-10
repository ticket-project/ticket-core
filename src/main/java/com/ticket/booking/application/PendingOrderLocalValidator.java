package com.ticket.booking.application;

import com.ticket.booking.hold.domain.HoldSeatAvailabilityValidator;
import com.ticket.booking.support.domain.RequestedSeatIds;
import com.ticket.booking.domain.OrderState;
import com.ticket.booking.domain.OrderRepository;
import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.booking.exception.PendingOrderAlreadyExistsException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 주문 생성 검증의 booking local DB 읽기만 담당한다. {@link CreateOrderValidator}가 다른 module
 * 공개 API를 트랜잭션 밖에서 호출한 뒤, 이 component가 짧은 읽기 트랜잭션 안에서 pending 주문
 * 중복과 좌석 판매 상태만 확인한다.
 *
 * <p>package-private component로 분리한 이유는 self-invocation을 피하기 위해서다. 같은 클래스
 * 안에서 이 method를 호출하면 {@code @Transactional} proxy가 적용되지 않는다.
 */
@Component
@RequiredArgsConstructor
class PendingOrderLocalValidator {

    private final OrderRepository orderRepository;
    private final HoldSeatAvailabilityValidator holdSeatAvailabilityValidator;

    @Transactional(readOnly = true)
    List<PerformanceSeat> validate(final Long memberId, final Long performanceId, final RequestedSeatIds requestedSeatIds) {
        ensureNoPendingOrder(memberId, performanceId);
        return holdSeatAvailabilityValidator.validate(performanceId, requestedSeatIds);
    }

    private void ensureNoPendingOrder(final Long memberId, final Long performanceId) {
        if (!orderRepository.existsByMemberIdAndPerformanceIdAndStatus(memberId, performanceId, OrderState.PENDING)) {
            return;
        }
        throw new PendingOrderAlreadyExistsException();
    }
}
