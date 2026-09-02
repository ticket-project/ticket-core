package com.ticket.catalog.internal.infrastructure.performance;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.catalog.internal.domain.performance.Performance;
import com.ticket.catalog.internal.domain.performance.query.PerformanceBookingPolicySnapshot;
import com.ticket.catalog.internal.domain.performance.repository.PerformanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static com.ticket.catalog.internal.domain.performance.QPerformance.performance;
import static com.ticket.catalog.internal.domain.performance.QPerformanceQueuePolicy.performanceQueuePolicy;

/**
 * {@link PerformanceRepository}의 JPA 구현이다.
 *
 * <p>정책 판정용 조회는 회차 엔티티를 적재하지 않고 필요한 컬럼만 projection한다.
 */
@Repository
@RequiredArgsConstructor
public class PerformanceRepositoryAdapter implements PerformanceRepository {

    private final SpringDataPerformanceJpaRepository jpaRepository;
    private final JPAQueryFactory queryFactory;

    @Override
    public List<Performance> findAllByShowIdOrderByStartTimeAscPerformanceNoAsc(final Long showId) {
        return jpaRepository.findAllByShowIdOrderByStartTimeAscPerformanceNoAsc(showId);
    }

    @Override
    public Optional<Performance> findWithQueuePolicyById(final Long performanceId) {
        return jpaRepository.findWithQueuePolicyById(performanceId);
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<PerformanceBookingPolicySnapshot> findBookingPolicyById(final Long performanceId) {
        return Optional.ofNullable(queryFactory
                .select(Projections.constructor(
                        PerformanceBookingPolicySnapshot.class,
                        performance.id,
                        performance.show.id,
                        performance.orderOpenTime,
                        performance.orderCloseTime,
                        performance.maxCanHoldCount,
                        performance.holdTime,
                        performanceQueuePolicy.queueMode,
                        performanceQueuePolicy.queueLevel,
                        performanceQueuePolicy.preopenQueueStartAt,
                        performanceQueuePolicy.waitingRoomMessage,
                        performanceQueuePolicy.reason
                ))
                .from(performance)
                .leftJoin(performance.queuePolicy, performanceQueuePolicy)
                .where(performance.id.eq(performanceId))
                .fetchOne());
    }
}
