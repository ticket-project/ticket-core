package com.ticket.booking.internal.application.order.query;

import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import com.ticket.booking.internal.domain.order.OrderRemainingTime;
import com.ticket.booking.internal.domain.order.model.OrderState;
import com.ticket.booking.internal.application.order.query.model.OrderDetailRow;
import com.ticket.catalog.PerformanceSummary;
import com.ticket.catalog.ShowLookup;
import com.ticket.catalog.ShowSeatMapEntry;
import com.ticket.catalog.ShowSummary;
import com.ticket.identity.MemberLookup;
import com.ticket.identity.MemberProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.ticket.shared.RequiredInput;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetOrderDetailUseCase {

    private final OrderReadRepository orderReadRepository;
    private final ShowLookup showLookup;
    private final MemberLookup memberLookup;
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
        // memberLookup.getProfile()은 탈퇴하거나 존재하지 않는 회원이면 NOT_FOUND_DATA를 던진다 —
        // 탈퇴한 회원의 주문은 본인에게도 보이지 않는다는 기존 규칙을 그대로 잇는다.
        final MemberProfile member = memberLookup.getProfile(first.memberId());

        final PerformanceSummary performanceSummary = requirePerformanceSummary(first.performanceId());
        final ShowSummary showSummary = requireShowSummary(performanceSummary.showId());
        final Map<Long, ShowSeatMapEntry> seatMapBySeatId = showLookup.getSeatMap(performanceSummary.showId())
                .stream()
                .collect(Collectors.toMap(ShowSeatMapEntry::seatId, entry -> entry));

        final LocalDateTime now = LocalDateTime.now(clock);
        final List<TicketSeat> seats = rows.stream()
                .map(row -> toTicketSeat(row, seatMapBySeatId.get(row.seatId())))
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
                new ShowInfo(performanceSummary.showId(), showSummary.title(), showSummary.image()),
                new PerformanceInfo(
                        performanceSummary.performanceId(),
                        performanceSummary.performanceNo(),
                        performanceSummary.startTime(),
                        showSummary.venueName()
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

    private PerformanceSummary requirePerformanceSummary(final Long performanceId) {
        final PerformanceSummary summary = showLookup.getPerformanceSummaries(Set.of(performanceId)).get(performanceId);
        if (summary == null) {
            throw new CoreException(ErrorType.NOT_FOUND_DATA);
        }
        return summary;
    }

    private ShowSummary requireShowSummary(final long showId) {
        final ShowSummary summary = showLookup.getSummaries(Set.of(showId)).get(showId);
        if (summary == null) {
            throw new CoreException(ErrorType.NOT_FOUND_DATA);
        }
        return summary;
    }

    private TicketSeat toTicketSeat(final OrderDetailRow row, final ShowSeatMapEntry entry) {
        if (entry == null) {
            throw new CoreException(ErrorType.NOT_FOUND_DATA);
        }
        final String label = entry.floor() + "F "
                + entry.section() + "구역 "
                + entry.rowNo() + "열 "
                + entry.seatNo() + "번";

        return new TicketSeat(
                row.performanceSeatId(),
                row.seatId(),
                entry.floor(),
                entry.section(),
                entry.rowNo(),
                entry.seatNo(),
                label,
                row.price()
        );
    }
}
