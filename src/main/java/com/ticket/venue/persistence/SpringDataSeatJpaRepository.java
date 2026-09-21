package com.ticket.venue.persistence;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ticket.venue.api.VenueSeatAddress;
import com.ticket.venue.api.VenueSeatLayout;
import com.ticket.venue.domain.Seat;

/**
 * 물리 좌석 조회다. {@code Seat.venueId}는 Venue가 다른 aggregate라 raw 컬럼이므로 join 없이 그대로 건다.
 *
 * <p>주소와 배치 좌표를 한 타입으로 합치지 않는다 — 좌석 주소는 주문·티켓 표시에, 좌표는 seat-map 렌더에 쓰여 소비자가 다르다.
 */
interface SpringDataSeatJpaRepository extends JpaRepository<Seat, Long> {
    @Query(
            """
            SELECT new com.ticket.venue.api.VenueSeatAddress(
                   s.id, s.floor, s.section, s.rowNo, s.seatNo)
            FROM Seat s
            WHERE s.venueId = :venueId AND s.id IN :seatIds
            """)
    List<VenueSeatAddress> findAddressesByVenueIdAndIdIn(
            @Param("venueId") long venueId, @Param("seatIds") Set<Long> seatIds);

    @Query(
            """
            SELECT new com.ticket.venue.api.VenueSeatLayout(
                   s.id, s.floor, s.section, s.rowNo, s.seatNo, s.x, s.y)
            FROM Seat s
            WHERE s.venueId = :venueId
            """)
    List<VenueSeatLayout> findLayoutsByVenueId(@Param("venueId") long venueId);
}
