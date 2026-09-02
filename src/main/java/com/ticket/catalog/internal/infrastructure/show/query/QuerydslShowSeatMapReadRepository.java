package com.ticket.catalog.internal.infrastructure.show.query;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.catalog.ShowSeatMapEntry;
import com.ticket.catalog.internal.application.show.query.ShowSeatMapReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.ticket.catalog.internal.domain.seat.QSeat.seat;
import static com.ticket.catalog.internal.domain.show.QShowGrade.showGrade;
import static com.ticket.catalog.internal.domain.show.QShowSeat.showSeat;

/**
 * {@link ShowSeatMapReadRepository}의 catalog 소유 구현이다. booking data를 참조하지 않는다 —
 * PerformanceSeat 판매 상태는 이 조회의 관심사가 아니다.
 */
@Repository
@RequiredArgsConstructor
public class QuerydslShowSeatMapReadRepository implements ShowSeatMapReadRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<ShowSeatMapEntry> findSeatMap(final Long showId) {
        return queryFactory
                .select(Projections.constructor(ShowSeatMapEntry.class,
                        seat.id,
                        seat.floor,
                        seat.section,
                        seat.rowNo,
                        seat.seatNo,
                        seat.x,
                        seat.y,
                        showGrade.gradeCode,
                        showGrade.gradeName,
                        showGrade.price,
                        showGrade.sortOrder
                ))
                .from(showSeat)
                .join(seat).on(seat.id.eq(showSeat.seat.id))
                .join(showGrade).on(showGrade.id.eq(showSeat.showGrade.id))
                .where(showSeat.show.id.eq(showId))
                .orderBy(seat.floor.asc(), seat.section.asc(), seat.rowNo.asc(), seat.seatNo.asc())
                .fetch();
    }
}
