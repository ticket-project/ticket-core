package com.ticket.booking.selection.application.usecase;

import com.ticket.booking.seat.application.SeatStatusEvent;

import com.ticket.booking.selection.domain.SeatSelectionService;
import com.ticket.booking.selection.domain.DeselectedSeatIds;
import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.booking.seat.domain.PerformanceSeatRepository;
import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.member.MemberLookup;
import com.ticket.booking.seat.application.SeatStatusEvent.SeatStatusAction;
import com.ticket.booking.seat.application.SeatStatusEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DeselectAllSeatsUseCase {

    private final MemberLookup memberLookup;
    private final SeatSelectionService seatSelectionService;
    private final PerformanceSeatRepository performanceSeatRepository;
    private final SeatStatusEventPublisher seatEventPublisher;

    public record Input(Long performanceId, Long memberId) {
        public Input {
            if (performanceId == null) {
                throw new InvalidRequestException("performanceId는 필수입니다.");
            }
            if (performanceId <= 0) {
                throw new InvalidRequestException("performanceId는 양수여야 합니다.");
            }
            if (memberId == null) {
                throw new InvalidRequestException("memberId는 필수입니다.");
            }
            if (memberId <= 0) {
                throw new InvalidRequestException("memberId는 양수여야 합니다.");
            }
        }
    }

    public void execute(final Input input) {
        memberLookup.requireActive(input.memberId());
        final DeselectedSeatIds seatIds = seatSelectionService.deselectAll(input.performanceId(), input.memberId());
        final Map<Long, Long> performanceSeatIdBySeatId = resolvePerformanceSeatIds(input.performanceId(), seatIds);
        seatIds.forEach(seatId -> seatEventPublisher.publish(
                input.performanceId(), performanceSeatIdBySeatId.get(seatId), SeatStatusAction.DESELECTED));
    }

    private Map<Long, Long> resolvePerformanceSeatIds(final Long performanceId, final DeselectedSeatIds seatIds) {
        final List<Long> ids = seatIds.values();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return performanceSeatRepository.findAllByPerformanceIdAndSeatIdIn(performanceId, ids).stream()
                .collect(java.util.stream.Collectors.toMap(PerformanceSeat::getSeatId, PerformanceSeat::getId));
    }
}
