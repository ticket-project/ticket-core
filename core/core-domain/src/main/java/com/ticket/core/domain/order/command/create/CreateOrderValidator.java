package com.ticket.core.domain.order.command.create;

import com.ticket.core.domain.member.query.MemberFinder;
import com.ticket.core.domain.order.model.OrderState;
import com.ticket.core.domain.order.repository.OrderRepository;
import com.ticket.core.domain.performance.query.PerformanceBookingPolicyFinder;
import com.ticket.core.domain.performance.query.model.PerformanceBookingPolicyView;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class CreateOrderValidator {

    private final MemberFinder memberFinder;
    private final PerformanceBookingPolicyFinder performanceBookingPolicyFinder;
    private final OrderRepository orderRepository;

    public PerformanceBookingPolicyView validate(
            final Long memberId,
            final Long performanceId,
            final RequestedSeatIds requestedSeatIds,
            final LocalDateTime now
    ) {
        memberFinder.ensureActiveMemberExists(memberId);
        final PerformanceBookingPolicyView policy = performanceBookingPolicyFinder.findById(performanceId);
        policy.ensureBookingOpenAt(now);
        validateSeatCount(policy, requestedSeatIds);
        ensureNoPendingOrder(memberId, performanceId);
        return policy;
    }

    private void validateSeatCount(final PerformanceBookingPolicyView performance, final RequestedSeatIds requestedSeatIds) {
        if (!performance.isOverCount(requestedSeatIds.size())) {
            return;
        }
        throw new CoreException(ErrorType.EXCEED_HOLD_LIMIT);
    }

    private void ensureNoPendingOrder(final Long memberId, final Long performanceId) {
        if (!orderRepository.existsByMemberIdAndPerformanceIdAndStatus(memberId, performanceId, OrderState.PENDING)) {
            return;
        }
        throw new CoreException(ErrorType.PENDING_ORDER_ALREADY_EXISTS);
    }
}
