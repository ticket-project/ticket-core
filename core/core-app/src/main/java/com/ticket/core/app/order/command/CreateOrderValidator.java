package com.ticket.core.app.order.command;

import com.ticket.core.domain.order.command.create.ValidatedOrderRequest;
import com.ticket.core.domain.order.command.create.RequestedSeatIds;
import com.ticket.core.domain.hold.command.HoldSeatAvailabilityValidator;
import com.ticket.core.domain.member.query.MemberFinder;
import com.ticket.core.domain.order.model.OrderState;
import com.ticket.core.domain.order.repository.OrderRepository;
import com.ticket.core.domain.performance.query.BookingPolicyValidator;
import com.ticket.core.domain.performance.query.PerformanceBookingPolicyFinder;
import com.ticket.core.domain.performance.query.model.PerformanceBookingPolicyView;
import com.ticket.core.domain.performanceseat.model.PerformanceSeat;
import com.ticket.core.domain.queue.AdmissionGuard;
import com.ticket.support.error.CoreException;
import com.ticket.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CreateOrderValidator {

    private final MemberFinder memberFinder;
    private final PerformanceBookingPolicyFinder performanceBookingPolicyFinder;
    private final OrderRepository orderRepository;
    private final HoldSeatAvailabilityValidator holdSeatAvailabilityValidator;
    private final AdmissionGuard admissionGuard;

    /**
     * 주문 생성 전 검증을 비용 순서로 수행한다.
     *
     * <p>회차 정책을 한 번 조회해 오픈·마감, 좌석 수 한도, 입장 검사를 모두 판정하고(T1),
     * 그다음 DB 조회가 필요한 검증을 같은 트랜잭션에서 수행한다(T2). 한 트랜잭션으로 묶는 이유는
     * 커넥션 획득 횟수를 1회로 줄이는 것이다. Redis hold 생성은 이 경계가 닫힌 뒤에 수행한다.
     */
    @Transactional(readOnly = true)
    public ValidatedOrderRequest validate(
            final CreateOrderUseCase.Input input,
            final RequestedSeatIds requestedSeatIds,
            final LocalDateTime now
    ) {
        final Long performanceId = input.performanceId();
        final Long memberId = input.memberId();

        final PerformanceBookingPolicyView policy = performanceBookingPolicyFinder.findById(performanceId);
        BookingPolicyValidator.ensureBookingOpen(policy, now);
        BookingPolicyValidator.ensureWithinHoldLimit(policy, requestedSeatIds.size());
        ensureAdmitted(policy, memberId, input.admissionToken(), now);

        memberFinder.ensureActiveMemberExists(memberId);
        ensureNoPendingOrder(memberId, performanceId);
        final List<PerformanceSeat> performanceSeats =
                holdSeatAvailabilityValidator.validate(performanceId, requestedSeatIds);

        return new ValidatedOrderRequest(policy, performanceSeats);
    }

    private void ensureAdmitted(
            final PerformanceBookingPolicyView policy,
            final Long memberId,
            final String admissionToken,
            final LocalDateTime now
    ) {
        if (!BookingPolicyValidator.requiresQueue(policy, now)) {
            return;
        }
        admissionGuard.ensureAdmitted(policy.performanceId(), memberId, admissionToken);
    }

    private void ensureNoPendingOrder(final Long memberId, final Long performanceId) {
        if (!orderRepository.existsByMemberIdAndPerformanceIdAndStatus(memberId, performanceId, OrderState.PENDING)) {
            return;
        }
        throw new CoreException(ErrorType.PENDING_ORDER_ALREADY_EXISTS);
    }
}
