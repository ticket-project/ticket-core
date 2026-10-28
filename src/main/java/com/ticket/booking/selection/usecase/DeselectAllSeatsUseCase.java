package com.ticket.booking.selection.usecase;

import static com.ticket.shared.api.InputChecks.requirePositiveId;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.booking.seat.domain.PerformanceSeatRepository;
import com.ticket.booking.selection.domain.SeatSelectionService;

import lombok.RequiredArgsConstructor;

/**
 * 회원이 이 회차에서 선택한 좌석을 모두 해제한다.
 *
 * <p>회원별 선택 인덱스로 이 회원의 좌석만 찾으므로 Redis 호출과 좌석 락은 이 회원이 선택한 좌석 수만큼만 생긴다. 한도가 있는 회차에서는 그 수가 한도 이하다.
 */
@Service
@RequiredArgsConstructor
public class DeselectAllSeatsUseCase {
    private final SeatSelectionService seatSelectionService;
    private final PerformanceSeatRepository performanceSeatRepository;
    private final SeatSelectionWriter seatSelectionWriter;

    public record Input(Long performanceId, Long memberId) {
        public Input {
            performanceId = requirePositiveId(performanceId, "performanceId");
            memberId = requirePositiveId(memberId, "memberId");
        }
    }

    public void execute(final Input input) {
        final List<Long> seatIds =
                seatSelectionService.getSelectedSeatIds(input.performanceId(), input.memberId()).stream()
                        .sorted()
                        .toList();
        // 알림 표시값(performanceSeatId)은 한 번에 읽어 좌석마다 DB를 다시 읽지 않는다. 해제와 발행은 좌석마다 스크립트
        // 하나라, 그 사이 남이 다시 고른 좌석을 비었다고 알리지 않는다.
        final Map<Long, Long> performanceSeatIdBySeatId = resolvePerformanceSeatIds(input.performanceId(), seatIds);
        seatIds.forEach(seatId -> seatSelectionWriter.deselectIfOwned(
                input.performanceId(), seatId, input.memberId(), performanceSeatIdBySeatId.get(seatId)));
    }

    private Map<Long, Long> resolvePerformanceSeatIds(final Long performanceId, final List<Long> seatIds) {
        if (seatIds.isEmpty()) {
            return Map.of();
        }
        return performanceSeatRepository.findAllByPerformanceIdAndSeatIdIn(performanceId, seatIds).stream()
                .collect(Collectors.toMap(PerformanceSeat::getSeatId, PerformanceSeat::getId));
    }
}
