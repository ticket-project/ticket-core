package com.ticket.booking.internal.application.performanceseat.query;

import com.ticket.booking.internal.application.support.BookingPolicyGuard;
import com.ticket.booking.internal.domain.hold.command.HoldManager;
import com.ticket.booking.internal.domain.performanceseat.command.SeatSelectionService;
import com.ticket.admission.AdmissionVerifier;
import com.ticket.catalog.BookingPolicyLookup;
import com.ticket.catalog.BookingPolicySnapshot;
import com.ticket.booking.internal.application.performanceseat.query.model.SeatStateView;
import com.ticket.booking.internal.application.performanceseat.query.model.SeatStatus;
import com.ticket.error.InvalidRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GetSeatStatusUseCase {

    private final BookingPolicyLookup bookingPolicyLookup;
    private final SeatStateSnapshotReader seatStatusDbReader;
    private final SeatSelectionService seatSelectionService;
    private final HoldManager holdManager;
    private final AdmissionVerifier admissionVerifier;
    private final Clock clock;

    public record Input(Long performanceId, Long memberId, String admissionToken) {
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

    public record Output(
            List<SeatStateView> seats
    ) {}

    public Output execute(final Input input) {
        final Long performanceId = input.performanceId();
        final LocalDateTime now = LocalDateTime.now(clock);

        final BookingPolicySnapshot policy = bookingPolicyLookup.getBookingPolicy(performanceId);
        BookingPolicyGuard.ensureBookingOpen(policy, now);
        ensureAdmitted(policy, input);

        final List<SeatStateView> dbStates = seatStatusDbReader.read(performanceId);

        final Set<Long> redisOccupiedIds = mergeRedisOccupiedIds(performanceId);
        if (redisOccupiedIds.isEmpty()) {
            return new Output(dbStates);
        }

        final List<SeatStateView> merged = dbStates.stream()
                .map(seat -> redisOccupiedIds.contains(seat.seatId())
                        ? new SeatStateView(seat.seatId(), SeatStatus.OCCUPIED)
                        : seat)
                .toList();

        return new Output(merged);
    }

    private void ensureAdmitted(
            final BookingPolicySnapshot policy,
            final Input input
    ) {
        if (!policy.queueRequired()) {
            return;
        }
        admissionVerifier.verify(policy.performanceId(), input.memberId(), input.admissionToken());
    }

    private Set<Long> mergeRedisOccupiedIds(final Long performanceId) {
        final Set<Long> selectingSeatIds = seatSelectionService.getSelectingSeatIds(performanceId);
        final Set<Long> holdingSeatIds = holdManager.getHoldingSeatIds(performanceId);

        final Set<Long> occupiedSeatIds = HashSet.newHashSet(selectingSeatIds.size() + holdingSeatIds.size());
        occupiedSeatIds.addAll(selectingSeatIds);
        occupiedSeatIds.addAll(holdingSeatIds);
        return occupiedSeatIds;
    }
}
