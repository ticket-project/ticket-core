package com.ticket.booking.selection.usecase;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.booking.seat.domain.PerformanceSeatRepository;
import com.ticket.booking.selection.domain.SeatSelectionService;
import com.ticket.member.api.MemberLookupApi;
import com.ticket.shared.exception.InvalidRequestException;

import lombok.RequiredArgsConstructor;

/**
 * 회원이 이 회차에서 선택한 좌석을 모두 해제한다.
 *
 * <p>좌석 수만큼 Redis 호출과 좌석 락이 생긴다. 현재 대표 규모(회차당 좌석 600석, 한 회원의 동시 선택은 회차 Hold 한도 이하)에서는 한 자릿수 좌석이라
 * 회원별 인덱스를 새로 두지 않았다. 인덱스를 두면 선택·해제·TTL 만료 세 경로에서 인덱스와 좌석 키의 정합성을 따로 맞춰야 하므로, 실제로 비용이 문제가 되는 것을 측정한
 * 뒤에 도입한다.
 */
@Service
@RequiredArgsConstructor
public class DeselectAllSeatsUseCase {
    private final MemberLookupApi memberLookup;
    private final SeatSelectionService seatSelectionService;
    private final PerformanceSeatRepository performanceSeatRepository;
    private final SeatSelectionCoordinator seatSelectionCoordinator;

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
        final List<Long> seatIds =
                seatSelectionService.deselectAll(input.performanceId(), input.memberId());
        final Map<Long, Long> performanceSeatIdBySeatId =
                resolvePerformanceSeatIds(input.performanceId(), seatIds);
        // 실제로 해제된 좌석만 돌려받지만, 알리기 전에 좌석 락 안에서 현재 상태를 다시 확인한다 — 해제와 발행
        // 사이에 다른 사용자가 같은 좌석을 다시 선택했을 수 있다. performanceSeatId는 위에서 한 번에
        // 조회한 값을 그대로 넘겨 좌석마다 DB를 다시 읽지 않는다.
        seatIds.forEach(
                seatId ->
                        seatSelectionCoordinator.notifyReleasedIfFree(
                                input.performanceId(),
                                seatId,
                                performanceSeatIdBySeatId.get(seatId)));
    }

    private Map<Long, Long> resolvePerformanceSeatIds(
            final Long performanceId, final List<Long> seatIds) {
        if (seatIds.isEmpty()) {
            return Map.of();
        }
        return performanceSeatRepository
                .findAllByPerformanceIdAndSeatIdIn(performanceId, seatIds)
                .stream()
                .collect(
                        java.util.stream.Collectors.toMap(
                                PerformanceSeat::getSeatId, PerformanceSeat::getId));
    }
}
