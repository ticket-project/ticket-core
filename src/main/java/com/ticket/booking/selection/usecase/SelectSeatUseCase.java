package com.ticket.booking.selection.usecase;

import static com.ticket.shared.api.InputChecks.requirePositiveId;

import java.time.Clock;
import java.time.LocalDateTime;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import com.ticket.booking.admission.BookingEntryGuard;
import com.ticket.booking.exception.BookingErrorCode;
import com.ticket.booking.exception.BookingException;
import com.ticket.booking.hold.domain.HoldRegistry;
import com.ticket.booking.salespolicy.domain.PerformanceSalesPolicy;
import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.booking.seat.domain.PerformanceSeatRepository;
import com.ticket.booking.seat.domain.PerformanceSeatState;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SelectSeatUseCase {
    private final SeatSelectionWriter seatSelectionWriter;
    private final PerformanceSeatRepository performanceSeatRepository;
    private final HoldRegistry holdRegistry;
    private final BookingEntryGuard bookingEntryGuard;
    private final Clock clock;

    public record Input(
            Long performanceId,
            Long seatId,
            Long memberId,
            @Nullable String admissionToken) {
        public Input {
            performanceId = requirePositiveId(performanceId, "performanceId");
            seatId = requirePositiveId(seatId, "seatId");
            memberId = requirePositiveId(memberId, "memberId");
        }
    }

    public void execute(final Input input) {
        final LocalDateTime now = LocalDateTime.now(clock);

        final PerformanceSalesPolicy policy =
                bookingEntryGuard.check(input.performanceId(), input.memberId(), input.admissionToken(), now);

        final Long performanceSeatId = requireSelectableSeat(input.performanceId(), input.seatId());

        // SELECTED 발행은 SeatSelectionWriter가 좌석 락 안에서 한다 — 발행을 락 밖으로 빼면 뒤늦은 만료 알림이
        // 이 SELECTED 뒤에 끼어들 수 있다.
        seatSelectionWriter.select(
                input.performanceId(),
                input.seatId(),
                input.memberId(),
                performanceSeatId,
                policy.getBookingWindow().getClosesAt(),
                policy.maxSeatCount());
    }

    /**
     * 좌석 자체가 선택 가능한지 확인한다. 예매 가능 시각과 대기열 입장은 바로 위에서 회차 정책으로 이미 판정했으므로 여기서 다시 보지 않는다.
     *
     * <p><b>여기서 보는 hold 여부는 SeatSelectionWriter가 좌석 락 안에서 다시 보는 것과 같은 검증이 아니다.</b> 이것은 락을 잡기 전의 사전 확인이라, 확인과 선택 사이에 다른
     * 요청이 같은 좌석을 선점할 수 있다. 경쟁 조건의 답은 락 안의 재확인이고 이쪽은 빠른 실패용이다 — 중복처럼 보인다고 한쪽을 지우면 안 된다.
     *
     * @return 검증된 좌석의 performanceSeatId. 호출자가 WebSocket 이벤트 등 외부 식별자가 필요한 곳에 다시 쓸 수 있도록 돌려준다 — 이미 이 조회에서 로드했으므로 추가 조회가
     *     필요 없다.
     */
    private Long requireSelectableSeat(final Long performanceId, final Long seatId) {
        final PerformanceSeat seat = performanceSeatRepository
                .findSeatState(performanceId, seatId)
                .orElseThrow(() -> new BookingException(BookingErrorCode.E4000));

        if (seat.getState() != PerformanceSeatState.AVAILABLE) {
            throw new BookingException(BookingErrorCode.E3003);
        }
        if (holdRegistry.isHeld(performanceId, seatId)) {
            throw new BookingException(BookingErrorCode.E6000);
        }
        return seat.getId();
    }
}
