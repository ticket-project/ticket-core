package com.ticket.booking.selection.usecase;

import static com.ticket.shared.api.InputChecks.requirePositiveId;

import java.time.Clock;
import java.time.LocalDateTime;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import com.ticket.booking.admission.BookingEntryGuard;
import com.ticket.booking.exception.BookingErrorCode;
import com.ticket.booking.exception.BookingException;
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

        // 선점 확인·선택·SELECTED 발행은 저장소 스크립트 하나가 한다 — 뒤늦은 만료 알림이 이 SELECTED 뒤에 끼어들지 못한다.
        seatSelectionWriter.select(
                input.performanceId(),
                input.seatId(),
                input.memberId(),
                performanceSeatId,
                policy.getBookingWindow().getClosesAt(),
                policy.maxSeatCount());
    }

    /**
     * 좌석 자체가 판매 가능한 좌석인지 확인한다. 예매 가능 시각과 대기열 입장은 바로 위에서 회차 정책으로 이미 판정했으므로 여기서 다시 보지 않는다. 선점 여부는 선택 스크립트가 선택과 같은 순간에
     * 확인한다(E6000).
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
        return seat.getId();
    }
}
