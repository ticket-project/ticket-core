package com.ticket.booking.internal.application.performanceseat.command;

import com.ticket.booking.internal.domain.performanceseat.command.SeatSelectionService;
import com.ticket.booking.internal.domain.performanceseat.command.DeselectedSeatIds;
import com.ticket.booking.internal.domain.performanceseat.model.PerformanceSeat;
import com.ticket.booking.internal.domain.performanceseat.repository.PerformanceSeatRepository;
import com.ticket.error.InvalidRequestException;
import com.ticket.identity.MemberLookup;
import com.ticket.booking.internal.application.performanceseat.event.SeatStatusEvent.SeatStatusAction;
import com.ticket.booking.internal.application.performanceseat.event.SeatStatusEventPublisher;
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
