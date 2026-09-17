package com.ticket.booking.selection.usecase;

import org.springframework.stereotype.Service;

import com.ticket.shared.exception.InvalidRequestException;

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
        seatSelectionCoordinator.deselect(input.performanceId(), input.seatId(), input.memberId());
    }
}
