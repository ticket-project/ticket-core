package com.ticket.core.domain.performance.query;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.core.domain.performance.query.model.PerformanceBookingPolicyView;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static com.ticket.core.domain.performance.model.QPerformance.performance;
import static com.ticket.core.domain.performance.model.QPerformanceQueuePolicy.performanceQueuePolicy;

@Repository
@RequiredArgsConstructor
public class PerformanceBookingPolicyQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Cacheable(cacheNames = "performanceBookingPolicy", key = "#performanceId", sync = true)
    @Transactional(readOnly = true)
    public Optional<PerformanceBookingPolicyView> findByPerformanceId(final Long performanceId) {
        return Optional.ofNullable(queryFactory
                .select(Projections.constructor(
                        PerformanceBookingPolicyView.class,
                        performance.id,
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
