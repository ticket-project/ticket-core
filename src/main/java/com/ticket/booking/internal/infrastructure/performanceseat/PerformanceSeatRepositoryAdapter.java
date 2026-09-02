package com.ticket.booking.internal.infrastructure.performanceseat;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.booking.internal.domain.performanceseat.model.PerformanceSeat;
import com.ticket.booking.internal.domain.performanceseat.model.PerformanceSeatState;
import com.ticket.booking.internal.domain.performanceseat.query.model.SeatSelectionAvailabilitySnapshot;
import com.ticket.booking.internal.domain.performanceseat.repository.PerformanceSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static com.ticket.booking.internal.domain.performanceseat.model.QPerformanceSeat.performanceSeat;

/**
 * {@link PerformanceSeatRepository}의 JPA 구현이다.
 */
@Repository
@RequiredArgsConstructor
public class PerformanceSeatRepositoryAdapter implements PerformanceSeatRepository {

    private final SpringDataPerformanceSeatJpaRepository jpaRepository;
    private final JPAQueryFactory queryFactory;

    @Override
    public List<PerformanceSeat> findAllByPerformanceIdAndSeatIdIn(
            final Long performanceId,
            final Collection<Long> seatIds
    ) {
        return jpaRepository.findAllByPerformanceIdAndSeatIdIn(performanceId, seatIds);
    }

    @Override
    public List<PerformanceSeat> findAllByStateEquals(final PerformanceSeatState state) {
        return jpaRepository.findAllByStateEquals(state);
    }

    /**
     * 좌석 한 건만 조회한다. 회차 존재와 예매 가능 시각은 회차 정책이 판정하므로 조인이 필요 없고,
     * UK_PERFORMANCE_SEATS_PERFORMANCE_SEAT 유니크 인덱스를 그대로 탄다.
     */
    @Override
    public Optional<SeatSelectionAvailabilitySnapshot> findSelectableSeat(
            final Long performanceId,
            final Long seatId
    ) {
        return Optional.ofNullable(queryFactory
                .select(Projections.constructor(SeatSelectionAvailabilitySnapshot.class,
                        performanceSeat.id,
                        performanceSeat.state
                ))
                .from(performanceSeat)
                .where(
                        performanceSeat.performance.id.eq(performanceId),
                        performanceSeat.seat.id.eq(seatId)
                )
                .fetchOne());
    }
}
