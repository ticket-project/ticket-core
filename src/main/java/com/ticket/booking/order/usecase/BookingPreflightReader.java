package com.ticket.booking.order.usecase;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.booking.admission.BookingEntryGuard;
import com.ticket.booking.exception.BookingErrorCode;
import com.ticket.booking.exception.BookingException;
import com.ticket.booking.order.domain.OrderRepository;
import com.ticket.booking.salespolicy.domain.PerformanceSalesPolicy;
import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.booking.seat.domain.PerformanceSeatRepository;
import com.ticket.booking.seat.domain.PerformanceSeatState;
import com.ticket.member.api.MemberLookupApi;
import com.ticket.show.api.PerformanceSaleInfoApi;
import com.ticket.show.api.PerformanceSaleSnapshot;

import lombok.RequiredArgsConstructor;

/**
 * 예매를 시작해도 되는지 확인하고, 통과하면 주문을 만드는 데 필요한 값을 함께 돌려준다.
 *
 * <p><b>별도 bean인 이유는 트랜잭션 경계다.</b> 아래 확인과 조회는 외부 호출 없이 연달아 DB를 읽는다. 각자 트랜잭션을 열면 읽을 때마다 connection을 따로 빌리고, 붐빌 때는 빌릴 때마다
 * 줄을 선다. 그래서 읽기 전용 트랜잭션 하나로 묶어 connection을 한 번만 빌린다. 다른 module(회원·공연)의 공개 API도 이 트랜잭션에 참여한다. Redis 선점과 주문 저장은 이 트랜잭션
 * 밖({@link CreateOrderUseCase})에서 한다.
 *
 * <p>여기서 부르는 것은 읽기만 해야 한다. 트랜잭션이 읽기 전용이라 엔티티 변경은 flush되지 않고 쓰기 쿼리는 DB가 거부한다. 같은 클래스 안에서 부르면 Spring proxy가 적용되지 않아 트랜잭션이
 * 걸리지 않는다.
 */
@Component
@RequiredArgsConstructor
public class BookingPreflightReader {
    private final BookingEntryGuard bookingEntryGuard;
    private final MemberLookupApi memberLookupApi;
    private final OrderRepository orderRepository;
    private final PerformanceSeatRepository performanceSeatRepository;
    private final PerformanceSaleInfoApi performanceSaleInfoApi;

    /** 확인을 통과한 정책과 좌석, 주문에 남길 표시값. 뒤 단계가 다시 조회하지 않도록 함께 돌려준다. */
    public record Preflight(
            PerformanceSalesPolicy policy,
            List<PerformanceSeat> performanceSeats,
            PerformanceSaleSnapshot saleSnapshot) {}

    @Transactional(readOnly = true)
    public Preflight read(
            final Long memberId,
            final Long performanceId,
            final RequestedSeatIds requestedSeatIds,
            final @Nullable String admissionToken,
            final LocalDateTime now) {
        // 1. 지금 이 회차의 예매를 받을 수 있는가.
        final PerformanceSalesPolicy policy = bookingEntryGuard.check(performanceId, memberId, admissionToken, now);
        policy.ensureWithinHoldLimit(requestedSeatIds.size());

        // 2. 예매할 수 있는 회원인가. 인증은 토큰의 서명·만료만 보므로 탈퇴 회원은 여기서 걸러진다 --
        //    좌석을 실제로 점유하는 첫 지점이라 요청 입구가 아니라 여기서 한 번만 확인한다.
        memberLookupApi.requireActive(memberId);

        // 3. 같은 회원·회차에 진행 중인 PENDING 주문이 없고, 요청한 좌석이 모두 이 회차의 판매 가능한 좌석인가.
        ensureNoPendingOrder(memberId, performanceId);
        final List<PerformanceSeat> performanceSeats = requireAvailableSeats(performanceId, requestedSeatIds);

        // 4. 주문에 남길 표시값(공연·공연장 이름, 등급, 좌석 라벨). 금액은 여기서 오지 않는다 -- 좌석 단가만 쓴다(ADR 0005).
        final PerformanceSaleSnapshot saleSnapshot =
                performanceSaleInfoApi.getSaleSnapshot(performanceId, Set.copyOf(requestedSeatIds.toList()));

        return new Preflight(policy, performanceSeats, saleSnapshot);
    }

    private void ensureNoPendingOrder(final Long memberId, final Long performanceId) {
        if (orderRepository.existsPendingByMemberIdAndPerformanceId(memberId, performanceId)) {
            throw new BookingException(BookingErrorCode.E5004);
        }
    }

    private List<PerformanceSeat> requireAvailableSeats(
            final Long performanceId, final RequestedSeatIds requestedSeatIds) {
        final List<PerformanceSeat> performanceSeats =
                performanceSeatRepository.findAllByPerformanceIdAndSeatIdIn(performanceId, requestedSeatIds.toList());
        if (performanceSeats.size() != requestedSeatIds.size()) {
            throw new BookingException(BookingErrorCode.E4000);
        }

        final boolean hasUnavailableSeat =
                performanceSeats.stream().anyMatch(seat -> seat.getState() != PerformanceSeatState.AVAILABLE);
        if (hasUnavailableSeat) {
            throw new BookingException(BookingErrorCode.E3003);
        }
        return performanceSeats;
    }
}
