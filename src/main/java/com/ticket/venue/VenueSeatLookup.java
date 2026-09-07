package com.ticket.venue;

import java.util.List;
import java.util.Set;

/**
 * 다른 module이 venue 안의 물리 좌석 주소·배치 좌표를 조회할 때 쓰는 공개 계약이다.
 */
public interface VenueSeatLookup {

    /**
     * venue에 속한 좌석 중 요청한 seatId만 반환한다. 빈 {@code seatIds}는 빈 목록을 반환한다.
     */
    List<VenueSeatAddress> findSeatAddresses(long venueId, Set<Long> seatIds);

    /**
     * venue에 속한 모든 물리 좌석의 배치 좌표를 반환한다.
     */
    List<VenueSeatLayout> findAllSeatLayouts(long venueId);
}
