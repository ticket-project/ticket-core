package com.ticket.core.domain.order.query;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.core.domain.order.query.model.OrderDetailRow;
import com.ticket.core.domain.order.query.model.OrderStatusView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.ticket.core.domain.member.model.QMember.member;
import static com.ticket.core.domain.order.model.QOrder.order;
import static com.ticket.core.domain.order.model.QOrderSeat.orderSeat;
import static com.ticket.core.domain.performance.model.QPerformance.performance;
import static com.ticket.core.domain.performanceseat.model.QPerformanceSeat.performanceSeat;
import static com.ticket.core.domain.seat.model.QSeat.seat;
import static com.ticket.core.domain.show.model.QShow.show;
import static com.ticket.core.domain.show.venue.QVenue.venue;

@Repository
@RequiredArgsConstructor
public class OrderQueryRepository {

    private final JPAQueryFactory queryFactory;

    public List<OrderDetailRow> findDetailRows(final String orderKey, final Long memberId) {
        return queryFactory
                .select(Projections.constructor(OrderDetailRow.class,
                        order.orderKey,
                        order.status,
                        order.expiresAt,
                        show.id,
                        show.title,
                        show.image,
                        performance.id,
                        performance.performanceNo,
                        performance.startTime,
                        venue.name,
                        member.id,
                        member.name,
                        member.email.email,
                        member.deletedAt,
                        orderSeat.performanceSeatId,
                        orderSeat.seatId,
                        seat.floor,
                        seat.section,
                        seat.rowNo,
                        seat.seatNo,
                        orderSeat.price
                ))
                .from(order)
                .join(orderSeat).on(orderSeat.order.id.eq(order.id))
                .join(performanceSeat).on(performanceSeat.id.eq(orderSeat.performanceSeatId))
                .join(performanceSeat.performance, performance)
                .join(performance.show, show)
                .leftJoin(show.venue, venue)
                .join(performanceSeat.seat, seat)
                .join(member).on(member.id.eq(order.memberId))
                .where(
                        order.orderKey.eq(orderKey),
                        order.memberId.eq(memberId)
                )
                .orderBy(orderSeat.id.asc())
                .fetch();
    }

    public Optional<OrderStatusView> findStatus(final String orderKey, final Long memberId) {
        return Optional.ofNullable(queryFactory
                .select(Projections.constructor(OrderStatusView.class,
                        order.orderKey,
                        order.status,
                        order.expiresAt
                ))
                .from(order)
                .where(
                        order.orderKey.eq(orderKey),
                        order.memberId.eq(memberId)
                )
                .fetchOne());
    }
}
