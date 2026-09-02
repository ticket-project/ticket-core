package com.ticket.catalog.internal.infrastructure.performance.query;

import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.catalog.internal.application.performance.query.BookingSeatPriceReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.ticket.catalog.internal.domain.performance.QPerformance.performance;
import static com.ticket.catalog.internal.domain.show.QShowGrade.showGrade;
import static com.ticket.catalog.internal.domain.show.QShowSeat.showSeat;

@Repository
@RequiredArgsConstructor
public class QuerydslBookingSeatPriceReadRepository implements BookingSeatPriceReadRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public Map<Long, BigDecimal> findSeatPrices(final long performanceId, final List<Long> seatIds) {
        if (seatIds.isEmpty()) {
            return Map.of();
        }

        final List<Tuple> rows = queryFactory
                .select(showSeat.seat.id, showGrade.price)
                .from(performance)
                .join(showSeat).on(showSeat.show.eq(performance.show))
                .join(showSeat.showGrade, showGrade)
                .where(
                        performance.id.eq(performanceId),
                        showSeat.seat.id.in(seatIds)
                )
                .fetch();

        return rows.stream()
                .collect(Collectors.toMap(
                        row -> row.get(showSeat.seat.id),
                        row -> row.get(showGrade.price),
                        (first, second) -> first
                ));
    }
}
