package com.ticket.venue.persistence;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ticket.venue.api.Region;
import com.ticket.venue.api.VenueSummary;
import com.ticket.venue.domain.Venue;

/**
 * venue 표시값 조회다. 고정 projection이라 Querydsl 대신 생성자 표현식을 쓴다({@code docs/readability-guidelines.md}
 * §10).
 *
 * <p>{@code seatMapLayout}은 중첩 생성자 표현식으로 한 번에 만든다 — 컬럼을 tuple로 받아 다시 조립하면 조회 밖에 매핑 코드가 남는다.
 */
interface SpringDataVenueJpaRepository extends JpaRepository<Venue, Long> {
    @Query(
            """
            SELECT new com.ticket.venue.api.VenueSummary(
                   v.id, v.name, v.address, v.region, v.latitude, v.longitude, v.phone, v.imageUrl,
                   new com.ticket.venue.api.VenueSummary$SeatMapLayout(
                           v.viewBoxWidth, v.viewBoxHeight, v.seatDiameter))
            FROM Venue v
            WHERE v.id = :venueId
            """)
    Optional<VenueSummary> findSummaryById(@Param("venueId") long venueId);

    @Query(
            """
            SELECT new com.ticket.venue.api.VenueSummary(
                   v.id, v.name, v.address, v.region, v.latitude, v.longitude, v.phone, v.imageUrl,
                   new com.ticket.venue.api.VenueSummary$SeatMapLayout(
                           v.viewBoxWidth, v.viewBoxHeight, v.seatDiameter))
            FROM Venue v
            WHERE v.id IN :venueIds
            """)
    List<VenueSummary> findSummariesByIdIn(@Param("venueIds") Set<Long> venueIds);

    @Query("SELECT v.id FROM Venue v WHERE v.region = :region")
    List<Long> findIdsByRegion(@Param("region") Region region);
}
