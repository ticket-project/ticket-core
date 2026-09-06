package com.ticket.booking.internal.application.order.query;

import com.ticket.booking.internal.domain.order.OrderRemainingTime;
import com.ticket.booking.internal.domain.order.model.OrderState;
import com.ticket.booking.internal.application.order.query.model.OrderDetailRow;
import com.ticket.booking.internal.exception.OrderNotOwnedException;
import com.ticket.error.InvalidRequestException;
import com.ticket.member.MemberLookup;
import com.ticket.member.MemberProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 상세를 조회한다(ADR 0005). show/venue/등급/좌석 표시값은 catalog를 다시 조회하지 않고
 * Order/OrderSeat가 주문 생성 시점에 이미 남긴 snapshot을 그대로 쓴다 — catalog의 표시값이나
 * 가격이 나중에 바뀌어도 이 응답은 바뀌지 않는다. 회원의 현재 이름·이메일만 member의 공개 API로
 * 추가 조회한다(탈퇴 여부처럼 살아있는 값이라 snapshot 대상이 아니다).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetOrderDetailUseCase {

    private final OrderReadRepository orderReadRepository;
    private final MemberLookup memberLookup;
    private final Clock clock;

    public record Input(String orderKey, Long memberId) {
        public Input {
            if (orderKey == null || orderKey.isBlank()) {
                throw new InvalidRequestException("orderKey는 필수입니다.");
            }
            if (memberId == null) {
                throw new InvalidRequestException("memberId는 필수입니다.");
            }
            if (memberId <= 0) {
                throw new InvalidRequestException("memberId는 양수여야 합니다.");
            }
        }
    }
    public record Output(
            String orderKey,
            OrderState status,
            LocalDateTime expiresAt,
            long remainingSeconds,
            ShowInfo show,
            PerformanceInfo performance,
            BookerInfo booker,
            PriceInfo price,
            TicketInfo tickets
    ) {}

    public record ShowInfo(String title) {}

    public record PerformanceInfo(Long performanceId, LocalDateTime startTime, String venueName) {}

    public record BookerInfo(Long memberId, String name, String email) {}

    public record PriceInfo(
            BigDecimal ticketAmount,
            BigDecimal bookingFee,
            BigDecimal deliveryFee,
            BigDecimal discountAmount,
            BigDecimal totalAmount
    ) {}

    public record TicketInfo(int count, List<TicketSeat> seats) {}

    public record TicketSeat(
            Long performanceSeatId,
            Long seatId,
            String gradeCode,
            String gradeName,
            String label,
            BigDecimal price
    ) {}

    public Output execute(final Input input) {
        final List<OrderDetailRow> rows = orderReadRepository.findDetailRows(input.orderKey(), input.memberId());
        if (rows.isEmpty()) {
            throw new OrderNotOwnedException();
        }

        final OrderDetailRow first = rows.getFirst();
        // memberLookup.getProfile()은 탈퇴하거나 존재하지 않는 회원이면 NOT_FOUND_DATA를 던진다 —
        // 탈퇴한 회원의 주문은 본인에게도 보이지 않는다는 기존 규칙을 그대로 잇는다.
        final MemberProfile member = memberLookup.getProfile(first.memberId());

        final LocalDateTime now = LocalDateTime.now(clock);
        final List<TicketSeat> seats = rows.stream()
                .map(this::toTicketSeat)
                .toList();
        final BigDecimal ticketAmount = rows.stream()
                .map(OrderDetailRow::unitPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        final long remainingSeconds = OrderRemainingTime.seconds(first.status(), first.expiresAt(), now);

        return new Output(
                first.orderKey(),
                first.status(),
                first.expiresAt(),
                remainingSeconds,
                new ShowInfo(first.showTitleSnapshot()),
                new PerformanceInfo(
                        first.performanceId(),
                        first.performanceStartAtSnapshot(),
                        first.venueNameSnapshot()
                ),
                new BookerInfo(member.memberId(), member.name(), member.email()),
                new PriceInfo(
                        ticketAmount,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        ticketAmount
                ),
                new TicketInfo(seats.size(), seats)
        );
    }

    private TicketSeat toTicketSeat(final OrderDetailRow row) {
        return new TicketSeat(
                row.performanceSeatId(),
                row.seatId(),
                row.gradeCodeSnapshot(),
                row.gradeNameSnapshot(),
                row.seatLabelSnapshot(),
                row.unitPrice()
        );
    }
}
