package com.ticket.venue.usecase;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.venue.api.VenueSeatLookupApi;
import com.ticket.venue.api.VenueSeatSnapshot;
import com.ticket.venue.domain.Seat;
import com.ticket.venue.domain.SeatRepository;

import lombok.RequiredArgsConstructor;

/**
 * {@link VenueSeatLookupApi}의 venue 소유 구현이다.
 *
 * <p>{@link VenueLookupService}와 나눠 둔다 — Seat은 Venue와 다른 Aggregate라 {@code Seat.venueId}가 연관관계가 아니라
 * raw 컬럼이고, 좌석 조회는 venue 표시값을 전혀 보지 않는다. 한 구현이 둘을 함께 들면 Aggregate 경계가 코드에서 사라진다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeatLookupService implements VenueSeatLookupApi {
    private final SeatRepository seatRepository;

    @Override
    public List<VenueSeatSnapshot> findSeats(final long venueId, final Set<Long> seatIds) {
        if (seatIds.isEmpty()) {
            return List.of();
        }
        return seatRepository.findAllByVenueIdAndIdIn(venueId, seatIds).stream()
                .map(SeatLookupService::toLayout)
                .toList();
    }

    @Override
    public List<VenueSeatSnapshot> findAllSeatLayouts(final long venueId) {
        return seatRepository.findAllByVenueId(venueId).stream()
                .map(SeatLookupService::toLayout)
                .toList();
    }

    private static VenueSeatSnapshot toLayout(final Seat seat) {
        return new VenueSeatSnapshot(
                seat.getId(),
                seat.getFloor(),
                seat.getSection(),
                seat.getRowNo(),
                seat.getSeatNo(),
                seat.getX(),
                seat.getY());
    }
}
