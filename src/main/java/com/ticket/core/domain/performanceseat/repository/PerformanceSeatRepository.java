package com.ticket.core.domain.performanceseat.repository;

import com.ticket.core.domain.performanceseat.model.PerformanceSeat;
import com.ticket.core.domain.performanceseat.model.PerformanceSeatState;
import com.ticket.core.domain.performanceseat.query.model.SeatSelectionAvailabilitySnapshot;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 회차 좌석 aggregate의 복원을 담당하는 도메인 Repository다.
 */
public interface PerformanceSeatRepository {

    List<PerformanceSeat> findAllByPerformanceIdAndSeatIdIn(Long performanceId, Collection<Long> seatIds);

    List<PerformanceSeat> findAllByStateEquals(PerformanceSeatState state);

    /**
     * 좌석 선택 판정에 필요한 좌석 한 건의 상태만 반환한다.
     *
     * <p>고빈도 경로라 엔티티 전체를 적재하지 않고 유니크 인덱스를 그대로 타도록 좁혀 조회한다.
     */
    Optional<SeatSelectionAvailabilitySnapshot> findSelectableSeat(Long performanceId, Long seatId);
}
