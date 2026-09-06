package com.ticket.booking.domain.hold.command;

import com.ticket.booking.domain.hold.model.HoldHistory;
import com.ticket.booking.domain.hold.repository.HoldHistoryRepository;
import com.ticket.booking.domain.order.model.OrderSeat;
import com.ticket.booking.domain.performanceseat.model.PerformanceSeat;
import com.ticket.booking.domain.hold.model.HoldReleaseReason;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class HoldHistoryRecorder {

    private final HoldHistoryRepository holdHistoryRepository;

    public void recordCreated(
            final Long memberId,
            final Long performanceId,
            final String holdKey,
            final LocalDateTime occurredAt,
            final LocalDateTime expiresAt,
            final List<PerformanceSeat> performanceSeats
    ) {
        holdHistoryRepository.saveAll(performanceSeats.stream()
                .map(seat -> HoldHistory.created(
                        holdKey,
                        memberId,
                        performanceId,
                        seat.getId(),
                        seat.getSeatId(),
                        occurredAt,
                        expiresAt
                ))
                .toList());
    }

    public void recordCanceled(
            final Long memberId,
            final Long performanceId,
            final String holdKey,
            final LocalDateTime occurredAt,
            final List<OrderSeat> orderSeats
    ) {
        holdHistoryRepository.saveAll(orderSeats.stream()
                .map(seat -> HoldHistory.canceled(
                        holdKey,
                        memberId,
                        performanceId,
                        seat.getPerformanceSeatId(),
                        seat.getSeatId(),
                        occurredAt,
                        HoldReleaseReason.USER_CANCELED
                ))
                .toList());
    }

    public void recordExpired(
            final Long memberId,
            final Long performanceId,
            final String holdKey,
            final LocalDateTime occurredAt,
            final List<OrderSeat> orderSeats
    ) {
        holdHistoryRepository.saveAll(orderSeats.stream()
                .map(seat -> HoldHistory.expired(
                        holdKey,
                        memberId,
                        performanceId,
                        seat.getPerformanceSeatId(),
                        seat.getSeatId(),
                        occurredAt,
                        HoldReleaseReason.TTL_EXPIRED
                ))
                .toList());
    }
}
