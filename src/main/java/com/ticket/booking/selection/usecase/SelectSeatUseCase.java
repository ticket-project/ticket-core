package com.ticket.booking.selection.usecase;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.ticket.booking.application.AdmissionGuard;
import com.ticket.booking.application.PerformanceSaleFinder;
import com.ticket.booking.domain.salespolicy.PerformanceSalesPolicy;
import com.ticket.booking.domain.seat.PerformanceSeatRepository;
import com.ticket.booking.domain.seat.PerformanceSeatState;
import com.ticket.booking.domain.seat.PerformanceSeatStateSnapshot;
import com.ticket.booking.exception.NoAvailableSeatException;
import com.ticket.booking.exception.SeatAlreadyHeldException;
import com.ticket.booking.exception.SeatMismatchInPerformanceException;
import com.ticket.booking.hold.domain.HoldManager;
import com.ticket.shared.exception.InvalidRequestException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SelectSeatUseCase {
    private final PerformanceSaleFinder performanceSaleFinder;
    private final SeatSelectionCoordinator seatSelectionCoordinator;
    private final PerformanceSeatRepository performanceSeatRepository;
    private final HoldManager holdManager;
    private final AdmissionGuard admissionGuard;
    private final Clock clock;

    public record Input(Long performanceId, Long seatId, Long memberId, String admissionToken) {
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
        final LocalDateTime now = LocalDateTime.now(clock);

        final PerformanceSalesPolicy policy =
                performanceSaleFinder.requirePolicy(input.performanceId());
        policy.ensureAcceptingOrders(now);
        admissionGuard.verifyIfRequired(
                policy, input.performanceId(), input.memberId(), input.admissionToken(), now);

        final Long performanceSeatId = requireSelectableSeat(input.performanceId(), input.seatId());

        // SELECTED 발행은 coordinator가 좌석 락 안에서 한다 — 발행을 락 밖으로 빼면 뒤늦은 만료 알림이
        // 이 SELECTED 뒤에 끼어들 수 있다.
        seatSelectionCoordinator.select(
                input.performanceId(),
                input.seatId(),
                input.memberId(),
                performanceSeatId,
                policy.getOrderAcceptanceWindow().getClosesAt());
    }

    /**
     * 좌석 자체가 선택 가능한지 확인한다. 예매 가능 시각과 대기열 입장은 바로 위에서 회차 정책으로 이미 판정했으므로 여기서 다시 보지 않는다.
     *
     * <p><b>여기서 보는 hold 여부는 coordinator가 좌석 락 안에서 다시 보는 것과 같은 검증이 아니다.</b> 이것은 락을 잡기 전의 사전 확인이라,
     * 확인과 선택 사이에 다른 요청이 같은 좌석을 선점할 수 있다. 경쟁 조건의 답은 락 안의 재확인이고 이쪽은 빠른 실패용이다 — 중복처럼 보인다고 한쪽을 지우면 안
     * 된다.
     *
     * @return 검증된 좌석의 performanceSeatId. 호출자가 WebSocket 이벤트 등 외부 식별자가 필요한 곳에 다시 쓸 수 있도록 돌려준다 — 이미 이
     *     조회에서 로드했으므로 추가 조회가 필요 없다.
     */
    private Long requireSelectableSeat(final Long performanceId, final Long seatId) {
        final PerformanceSeatStateSnapshot seat =
                performanceSeatRepository
                        .findSeatState(performanceId, seatId)
                        .orElseThrow(() -> new SeatMismatchInPerformanceException(performanceId));

        if (seat.state() != PerformanceSeatState.AVAILABLE) {
            throw new NoAvailableSeatException(performanceId);
        }
        if (holdManager.isHeld(performanceId, seatId)) {
            throw new SeatAlreadyHeldException(performanceId, seatId);
        }
        return seat.performanceSeatId();
    }
}
