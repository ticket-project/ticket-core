package com.ticket.booking.order.usecase;

import static com.ticket.shared.api.InputChecks.requirePositiveId;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.booking.exception.BookingErrorCode;
import com.ticket.booking.exception.BookingException;
import com.ticket.booking.order.domain.Order;
import com.ticket.booking.order.domain.OrderRemainingTime;
import com.ticket.booking.order.domain.OrderRepository;
import com.ticket.booking.order.domain.OrderSeat;
import com.ticket.booking.order.domain.OrderState;
import com.ticket.member.api.MemberLookupApi;
import com.ticket.member.api.MemberSnapshot;
import com.ticket.shared.exception.InvalidRequestException;

import lombok.RequiredArgsConstructor;

/**
 * 주문 상세를 조회한다(ADR 0005). show/venue/등급/좌석 표시값은 show를 다시 조회하지 않고 Order/OrderSeat가 주문 생성 시점에 이미 남긴 snapshot을 그대로 쓴다 —
 * show의 표시값이나 가격이 나중에 바뀌어도 이 응답은 바뀌지 않는다. 회원의 현재 이름·이메일만 member의 공개 API로 추가 조회한다(탈퇴 여부처럼 살아있는 값이라 snapshot 대상이 아니다).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetOrderDetailUseCase {
    private final OrderRepository orderRepository;
    private final MemberLookupApi memberLookupApi;
    private final Clock clock;

    public Output execute(final Input input) {
        // 좌석까지 join fetch로 함께 읽는다 — 쿼리는 한 개이고 트랜잭션 안에서 좌석 접근이 끝난다.
        final Order order = orderRepository
                .findDetailByOrderKeyAndMemberId(input.orderKey(), input.memberId())
                .orElseThrow(() -> new BookingException(BookingErrorCode.E5003));

        // memberLookupApi.getProfile()은 탈퇴하거나 존재하지 않는 회원이면 NOT_FOUND_DATA를 던진다 —
        // 탈퇴한 회원의 주문은 본인에게도 보이지 않는다는 기존 규칙을 그대로 잇는다.
        final MemberSnapshot member = memberLookupApi.getProfile(order.getMemberId());

        final LocalDateTime now = LocalDateTime.now(clock);
        final List<TicketSeatResponse> seats =
                order.getOrderSeats().stream().map(this::toTicketSeatResponse).toList();
        final BigDecimal ticketAmount = order.getTotalAmount();
        final long remainingSeconds = OrderRemainingTime.seconds(order.getStatus(), order.getExpiresAt(), now);

        return new Output(
                order.getOrderKey(),
                order.getStatus(),
                order.getExpiresAt(),
                remainingSeconds,
                new ShowResponse(order.getShowTitleSnapshot()),
                new PerformanceResponse(
                        order.getPerformanceId(), order.getPerformanceStartAtSnapshot(), order.getVenueNameSnapshot()),
                new BookerResponse(member.memberId(), member.name(), member.email()),
                new PriceResponse(ticketAmount, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, ticketAmount),
                new TicketResponse(seats.size(), seats));
    }

    private TicketSeatResponse toTicketSeatResponse(final OrderSeat orderSeat) {
        return new TicketSeatResponse(
                orderSeat.getPerformanceSeatId(),
                orderSeat.getSeatId(),
                orderSeat.getGradeCodeSnapshot(),
                orderSeat.getGradeNameSnapshot(),
                orderSeat.getSeatLabelSnapshot(),
                orderSeat.getUnitPrice());
    }

    public record Input(String orderKey, Long memberId) {
        public Input {
            if (orderKey == null || orderKey.isBlank()) {
                throw new InvalidRequestException("orderKey는 필수입니다.");
            }
            memberId = requirePositiveId(memberId, "memberId");
        }
    }

    public record Output(
            String orderKey,
            OrderState status,
            LocalDateTime expiresAt,
            long remainingSeconds,
            ShowResponse show,
            PerformanceResponse performance,
            BookerResponse booker,
            PriceResponse price,
            TicketResponse tickets) {}

    public record ShowResponse(String title) {}

    public record PerformanceResponse(Long performanceId, LocalDateTime startTime, String venueName) {}

    public record BookerResponse(Long memberId, String name, String email) {}

    public record PriceResponse(
            BigDecimal ticketAmount,
            BigDecimal bookingFee,
            BigDecimal deliveryFee,
            BigDecimal discountAmount,
            BigDecimal totalAmount) {}

    public record TicketResponse(int count, List<TicketSeatResponse> seats) {}

    public record TicketSeatResponse(
            Long performanceSeatId, Long seatId, String gradeCode, String gradeName, String label, BigDecimal price) {}
}
