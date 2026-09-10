package com.ticket.booking.seat.domain;

import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.booking.seat.domain.PerformanceSeatState;
import com.ticket.booking.selection.domain.SeatSelectionAvailabilitySnapshot;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 회차 좌석 aggregate의 복원을 담당하는 도메인 Repository다.
 */
public interface PerformanceSeatRepository {

    /**
     * 판매 좌석 편성(PerformanceSeat 생성)을 저장한다. 이미 존재하는 (performanceId, seatId) 조합은
     * 호출하는 유스케이스가 저장 전에 걸러낸다 — 이 메서드는 DB unique 제약을 최후 방어선으로만
     * 둔다.
     */
    List<PerformanceSeat> saveAll(List<PerformanceSeat> performanceSeats);

    List<PerformanceSeat> findAllByPerformanceIdAndSeatIdIn(Long performanceId, Collection<Long> seatIds);

    List<PerformanceSeat> findAllByStateEquals(PerformanceSeatState state);

    /**
     * 좌석 선택 판정에 필요한 좌석 한 건의 상태만 반환한다.
     *
     * <p>고빈도 경로라 엔티티 전체를 적재하지 않고 유니크 인덱스를 그대로 타도록 좁혀 조회한다.
     */
    Optional<SeatSelectionAvailabilitySnapshot> findSelectableSeat(Long performanceId, Long seatId);
}
