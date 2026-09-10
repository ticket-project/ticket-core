package com.ticket.booking.order.application;

import com.ticket.booking.order.application.usecase.CreateOrderUseCase;

import com.ticket.booking.admission.application.AdmissionVerifier;
import com.ticket.booking.support.domain.RequestedSeatIds;
import com.ticket.booking.salespolicy.domain.PerformanceSalesPolicy;
import com.ticket.booking.salespolicy.domain.PerformanceSalesPolicyRepository;
import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.error.NotFoundException;
import com.ticket.show.PerformanceSaleCatalog;
import com.ticket.show.PerformanceSaleSnapshot;
import com.ticket.member.MemberLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CreateOrderValidator {

    private final MemberLookup memberLookup;
    private final PerformanceSalesPolicyRepository performanceSalesPolicyRepository;
    private final PerformanceSaleCatalog performanceSaleCatalog;
    private final AdmissionVerifier admissionVerifier;
    private final PendingOrderLocalValidator pendingOrderLocalValidator;

    /**
     * 주문 생성 전 검증을 비용 순서로 수행한다.
     *
     * <p>회원 활성 확인(member), 판매 정책 조회(booking local)·주문 표시 snapshot 조회(show), 필요 시
     * 입장 검사(admission)는 모두 booking DB 트랜잭션 밖에서 호출한다. 판매 정책이 local DB
     * 조회라는 이유로 show/member 호출까지 하나의 긴 트랜잭션에 넣지 않는다. booking local read
     * (pending 주문 중복, 좌석 판매 상태)만 {@link PendingOrderLocalValidator}의 짧은 읽기
     * 트랜잭션에서 수행한다. Redis hold 생성은 이 모든 검증이 끝난 뒤에 수행한다.
     *
     * <p>주문 금액은 여기서 조회한 show 표시값이 아니라 오직 {@code PerformanceSeat.unitPrice}로만
     * 계산한다(ADR 0005). {@link PerformanceSaleCatalog} snapshot은 Order/OrderSeat에 남길 표시값
     * (show/venue 이름, 등급 코드/이름, 좌석 라벨)만 제공한다.
     */
    public ValidatedOrderRequest validate(
            final CreateOrderUseCase.Input input,
            final RequestedSeatIds requestedSeatIds,
            final LocalDateTime now
    ) {
        final Long performanceId = input.performanceId();
        final Long memberId = input.memberId();

        final PerformanceSalesPolicy policy = findPolicy(performanceId);
        policy.ensureAcceptingOrders(now);
        policy.ensureWithinHoldLimit(requestedSeatIds.size());
        ensureAdmitted(policy, performanceId, memberId, input.admissionToken(), now);

        memberLookup.requireActive(memberId);

        final List<PerformanceSeat> performanceSeats =
                pendingOrderLocalValidator.validate(memberId, performanceId, requestedSeatIds);

        final PerformanceSaleSnapshot saleSnapshot = performanceSaleCatalog.getSaleSnapshot(
                performanceId, Set.copyOf(requestedSeatIds.toList()));

        return new ValidatedOrderRequest(policy, performanceSeats, saleSnapshot);
    }

    private PerformanceSalesPolicy findPolicy(final Long performanceId) {
        return performanceSalesPolicyRepository.findById(performanceId)
                .orElseThrow(() -> new NotFoundException(
                        "회차 판매 정책을 찾을 수 없습니다. id=" + performanceId));
    }

    private void ensureAdmitted(
            final PerformanceSalesPolicy policy,
            final Long performanceId,
            final Long memberId,
            final String admissionToken,
            final LocalDateTime now
    ) {
        if (!policy.isQueueRequired(now)) {
            return;
        }
        admissionVerifier.verify(performanceId, memberId, admissionToken);
    }
}
