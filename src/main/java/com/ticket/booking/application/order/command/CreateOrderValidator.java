package com.ticket.booking.application.order.command;

import com.ticket.booking.application.admission.AdmissionVerifier;
import com.ticket.booking.application.support.BookingPolicyGuard;
import com.ticket.booking.domain.order.command.create.RequestedSeatIds;
import com.ticket.booking.domain.order.command.create.ValidatedOrderRequest;
import com.ticket.booking.domain.performanceseat.model.PerformanceSeat;
import com.ticket.catalog.BookingPolicyLookup;
import com.ticket.catalog.BookingPolicySnapshot;
import com.ticket.catalog.PerformanceSaleCatalog;
import com.ticket.catalog.PerformanceSaleSnapshot;
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
    private final BookingPolicyLookup bookingPolicyLookup;
    private final PerformanceSaleCatalog performanceSaleCatalog;
    private final AdmissionVerifier admissionVerifier;
    private final PendingOrderLocalValidator pendingOrderLocalValidator;

    /**
     * 주문 생성 전 검증을 비용 순서로 수행한다.
     *
     * <p>회원 활성 확인(member), 예매 정책 조회·주문 표시 snapshot 조회(catalog), 필요 시 입장 검사
     * (admission)는 모두 booking DB 트랜잭션 밖에서 호출한다. 다른 module 호출이 booking 트랜잭션
     * 안에 있으면 그 module의 지연이나 실패가 booking connection을 붙잡는다. booking local read
     * (pending 주문 중복, 좌석 판매 상태)만 {@link PendingOrderLocalValidator}의 짧은 읽기
     * 트랜잭션에서 수행한다. Redis hold 생성은 이 모든 검증이 끝난 뒤에 수행한다.
     *
     * <p>주문 금액은 여기서 조회한 catalog 표시값이 아니라 오직 {@code PerformanceSeat.unitPrice}로만
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

        final BookingPolicySnapshot policy = bookingPolicyLookup.getBookingPolicy(performanceId);
        BookingPolicyGuard.ensureBookingOpen(policy, now);
        BookingPolicyGuard.ensureWithinHoldLimit(policy, requestedSeatIds.size());
        ensureAdmitted(policy, memberId, input.admissionToken());

        memberLookup.requireActive(memberId);

        final List<PerformanceSeat> performanceSeats =
                pendingOrderLocalValidator.validate(memberId, performanceId, requestedSeatIds);

        final PerformanceSaleSnapshot saleSnapshot = performanceSaleCatalog.getSaleSnapshot(
                performanceId, Set.copyOf(requestedSeatIds.toList()));

        return new ValidatedOrderRequest(policy, performanceSeats, saleSnapshot);
    }

    private void ensureAdmitted(
            final BookingPolicySnapshot policy,
            final Long memberId,
            final String admissionToken
    ) {
        if (!policy.queueRequired()) {
            return;
        }
        admissionVerifier.verify(policy.performanceId(), memberId, admissionToken);
    }
}
