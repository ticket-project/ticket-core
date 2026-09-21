package com.ticket.venue.persistence;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.venue.api.VenueSeatAddress;
import com.ticket.venue.api.VenueSeatLayout;
import com.ticket.venue.api.VenueSeatLookupApi;

import lombok.RequiredArgsConstructor;

/**
 * {@link VenueSeatLookupApi}의 venue 소유 구현이다.
 *
 * <p>{@link VenueRepositoryAdapter}와 나눠 둔다 — Seat은 Venue와 다른 Aggregate라 {@code Seat.venueId}가 연관관계가
 * 아니라 raw 컬럼이고, 좌석 조회는 venue 표시값을 전혀 보지 않는다. 한 adapter가 둘을 함께 들면 Aggregate 경계가 코드에서 사라진다.
 *
 * <p>좌석 주소와 배치 좌표를 한 계약으로 합치지 않는 이유는 {@link SpringDataSeatJpaRepository}에 적어 뒀다.
 */
@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeatRepositoryAdapter implements VenueSeatLookupApi {
    private final SpringDataSeatJpaRepository seatJpaRepository;

    @Override
    public List<VenueSeatAddress> findSeatAddresses(final long venueId, final Set<Long> seatIds) {
        if (seatIds.isEmpty()) {
            return List.of();
        }
        return seatJpaRepository.findAddressesByVenueIdAndIdIn(venueId, seatIds);
    }

    @Override
    public List<VenueSeatLayout> findAllSeatLayouts(final long venueId) {
        return seatJpaRepository.findLayoutsByVenueId(venueId);
    }
}
