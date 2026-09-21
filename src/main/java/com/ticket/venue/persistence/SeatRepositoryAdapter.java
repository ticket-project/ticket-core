package com.ticket.venue.persistence;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.venue.api.VenueSeatLayout;
import com.ticket.venue.api.VenueSeatLookupApi;
import com.ticket.venue.domain.Seat;

import lombok.RequiredArgsConstructor;

/**
 * {@link VenueSeatLookupApi}의 venue 소유 구현이다.
 *
 * <p>{@link VenueRepositoryAdapter}와 나눠 둔다 — Seat은 Venue와 다른 Aggregate라 {@code Seat.venueId}가 연관관계가
 * 아니라 raw 컬럼이고, 좌석 조회는 venue 표시값을 전혀 보지 않는다. 한 adapter가 둘을 함께 들면 Aggregate 경계가 코드에서 사라진다.
 *
 * <p>조회는 {@code Seat} 엔티티를 받고 공개 계약 타입으로의 변환은 여기서 한 번만 한다. 주소와 배치 좌표를 한 타입으로 합쳤으므로 두 조회의 변환도 하나다 —
 * 좌석 주소는 {@code VenueSeatLayout}의 부분집합이다.
 */
@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeatRepositoryAdapter implements VenueSeatLookupApi {
    private final SpringDataSeatJpaRepository seatJpaRepository;

    @Override
    public List<VenueSeatLayout> findSeats(final long venueId, final Set<Long> seatIds) {
        if (seatIds.isEmpty()) {
            return List.of();
        }
        return seatJpaRepository.findAllByVenueIdAndIdIn(venueId, seatIds).stream()
                .map(SeatRepositoryAdapter::toLayout)
                .toList();
    }

    @Override
    public List<VenueSeatLayout> findAllSeatLayouts(final long venueId) {
        return seatJpaRepository.findAllByVenueId(venueId).stream()
                .map(SeatRepositoryAdapter::toLayout)
                .toList();
    }

    private static VenueSeatLayout toLayout(final Seat seat) {
        return new VenueSeatLayout(
                seat.getId(),
                seat.getFloor(),
                seat.getSection(),
                seat.getRowNo(),
                seat.getSeatNo(),
                seat.getX(),
                seat.getY());
    }
}
