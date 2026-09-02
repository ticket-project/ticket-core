package com.ticket.catalog.internal.application.show.query;

import com.ticket.catalog.ShowSeatMapEntry;

import java.util.List;

/**
 * {@link com.ticket.catalog.ShowLookup#getSeatMap(long)}가 쓰는 조회 포트다.
 */
public interface ShowSeatMapReadRepository {

    /**
     * show와 연결된 좌석이 없으면 빈 목록을 반환한다.
     */
    List<ShowSeatMapEntry> findSeatMap(Long showId);
}
