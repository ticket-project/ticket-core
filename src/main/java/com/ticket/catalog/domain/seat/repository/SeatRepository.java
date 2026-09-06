package com.ticket.catalog.domain.seat.repository;

import com.ticket.catalog.domain.seat.Seat;

import java.util.List;

/**
 * 물리 좌석 aggregate의 복원을 담당하는 도메인 Repository다.
 */
public interface SeatRepository {

    List<Seat> findAllByVenueId(Long venueId);
}
