package com.ticket.booking.selection.application.usecase;

import com.ticket.booking.seat.application.SeatStatusEvent;

import com.ticket.booking.selection.domain.SeatSelectionService;
import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.booking.seat.domain.PerformanceSeatRepository;
import com.ticket.booking.seat.application.SeatStatusEvent.SeatStatusAction;
import com.ticket.booking.seat.application.SeatStatusEventPublisher;
import com.ticket.shared.exception.InvalidRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeselectSeatUseCase {

    private final SeatSelectionService seatSelectionService;
    private final PerformanceSeatRepository performanceSeatRepository;
    private final SeatStatusEventPublisher seatEventPublisher;

    public record Input(Long performanceId, Long seatId, Long memberId) {
        public Input {
            if (performanceId == null) {
                throw new InvalidRequestException("performanceId는 필수입니다.");
            }
            if (performanceId <= 0) {
                throw new InvalidRequestException("performanceId는 양수여야 합니다.");
            }
            if (seatId == null) {
                throw new InvalidRequestException("seatId는 필수입니다.");
            }
            if (seatId <= 0) {
                throw new InvalidRequestException("seatId는 양수여야 합니다.");
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
        seatSelectionService.deselect(input.performanceId(), input.seatId(), input.memberId());
        final Long performanceSeatId = resolvePerformanceSeatId(input.performanceId(), input.seatId());
        seatEventPublisher.publish(input.performanceId(), performanceSeatId, SeatStatusAction.DESELECTED);
    }

    private Long resolvePerformanceSeatId(final Long performanceId, final Long seatId) {
        return performanceSeatRepository.findAllByPerformanceIdAndSeatIdIn(performanceId, List.of(seatId))
                .stream()
                .findFirst()
                .map(PerformanceSeat::getId)
                .orElse(null);
    }
}
