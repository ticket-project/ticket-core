package com.ticket.booking.selection.usecase;

import static com.ticket.shared.api.InputChecks.requirePositiveId;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * 좌석 한 자리의 선택을 해제한다.
 *
 * <p>해제 여부 판단과 좌석 상태 알림은 {@link SeatSelectionCoordinator}가 좌석 락 안에서 함께 처리한다 — 예전에는 실제 해제 여부와 무관하게
 * 여기서 DESELECTED를 발행해, 이미 만료된 선택을 해제 요청하면 남이 다시 잡은 좌석까지 비었다고 알렸다.
 */
@Service
@RequiredArgsConstructor
public class DeselectSeatUseCase {
    private final SeatSelectionCoordinator seatSelectionCoordinator;

    public record Input(Long performanceId, Long seatId, Long memberId) {
        public Input {
            performanceId = requirePositiveId(performanceId, "performanceId");
            seatId = requirePositiveId(seatId, "seatId");
            memberId = requirePositiveId(memberId, "memberId");
        }
    }

    public void execute(final Input input) {
        seatSelectionCoordinator.deselect(input.performanceId(), input.seatId(), input.memberId());
    }
}
