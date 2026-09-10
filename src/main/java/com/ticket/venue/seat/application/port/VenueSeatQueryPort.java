package com.ticket.venue.seat.application.port;

import com.ticket.venue.VenueSeatAddress;
import com.ticket.venue.VenueSeatLayout;

import java.util.List;
import java.util.Set;

/**
 * {@link com.ticket.venue.VenueSeatLookup}이 쓰는 물리 좌석 조회 포트다.
 */
public interface VenueSeatQueryPort {

    /**
     * 빈 {@code seatIds}는 빈 목록을 반환한다.
     */
    List<VenueSeatAddress> findSeatAddresses(long venueId, Set<Long> seatIds);

    List<VenueSeatLayout> findAllSeatLayouts(long venueId);
}
