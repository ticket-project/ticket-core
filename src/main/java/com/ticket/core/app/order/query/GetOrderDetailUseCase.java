package com.ticket.core.app.order.query;

import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import com.ticket.core.support.exception.ErrorType;
import com.ticket.core.domain.order.OrderRemainingTime;
import com.ticket.core.domain.order.model.OrderState;
import com.ticket.core.app.order.query.model.OrderDetailRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import com.ticket.core.app.support.validation.RequiredInput;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetOrderDetailUseCase {

    private final OrderReadRepository orderReadRepository;
    private final Clock clock;

    public record Input(String orderKey, Long memberId) {
        public Input {
            RequiredInput.notBlank(orderKey, "orderKey");
            RequiredInput.positiveId(memberId, "memberId");
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

    public record ShowInfo(Long showId, String title, String imageUrl) {}

    public record PerformanceInfo(Long performanceId, Long performanceNo, LocalDateTime startTime, String venueName) {}

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
            int floor,
            String section,
            String rowNo,
            String seatNo,
            String label,
            BigDecimal price
    ) {}

    public Output execute(final Input input) {
        final List<OrderDetailRow> rows = orderReadRepository.findDetailRows(input.orderKey(), input.memberId());
        if (rows.isEmpty()) {
            throw new CoreException(ErrorType.ORDER_NOT_OWNED);
        }

        final OrderDetailRow first = rows.getFirst();
        if (first.memberDeletedAt() != null) {
            throw new CoreException(ErrorType.NOT_FOUND_DATA);
        }

        final LocalDateTime now = LocalDateTime.now(clock);
        final List<TicketSeat> seats = rows.stream()
                .map(this::toTicketSeat)
                .toList();
        final BigDecimal ticketAmount = rows.stream()
                .map(OrderDetailRow::price)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        final long remainingSeconds = OrderRemainingTime.seconds(first.status(), first.expiresAt(), now);

        return new Output(
                first.orderKey(),
                first.status(),
                first.expiresAt(),
                remainingSeconds,
                new ShowInfo(first.showId(), first.showTitle(), first.showImageUrl()),
                new PerformanceInfo(
                        first.performanceId(),
                        first.performanceNo(),
                        first.startTime(),
                        first.venueName()
                ),
                new BookerInfo(first.memberId(), first.memberName(), first.memberEmail()),
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
        final String label = row.floor() + "F "
                + row.section() + "구역 "
                + row.rowNo() + "열 "
                + row.seatNo() + "번";

        return new TicketSeat(
                row.performanceSeatId(),
                row.seatId(),
                row.floor(),
                row.section(),
                row.rowNo(),
                row.seatNo(),
                label,
                row.price()
        );
    }
}
