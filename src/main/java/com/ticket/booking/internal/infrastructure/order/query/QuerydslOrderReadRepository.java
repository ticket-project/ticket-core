package com.ticket.booking.internal.infrastructure.order.query;

import com.ticket.booking.internal.application.order.query.OrderReadRepository;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.booking.internal.application.order.query.model.OrderDetailRow;
import com.ticket.booking.internal.application.order.query.model.OrderStatusView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.ticket.identity.internal.domain.member.model.QMember.member;
import static com.ticket.booking.internal.domain.order.model.QOrder.order;
import static com.ticket.booking.internal.domain.order.model.QOrderSeat.orderSeat;
import static com.ticket.catalog.internal.domain.performance.QPerformance.performance;
import static com.ticket.booking.internal.domain.performanceseat.model.QPerformanceSeat.performanceSeat;
import static com.ticket.catalog.internal.domain.seat.QSeat.seat;
import static com.ticket.catalog.internal.domain.show.QShow.show;
import static com.ticket.catalog.internal.domain.show.QVenue.venue;

@Repository
@RequiredArgsConstructor
public class QuerydslOrderReadRepository implements OrderReadRepository {

    private final JPAQueryFactory queryFactory;

    @Override
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

    @Override
    public Optional<OrderStatusView> findStatus(final String orderKey, final Long memberId) {
        return Optional.ofNullable(queryFactory
                .select(Projections.constructor(OrderStatusView.class,
                        order.orderKey,
                        order.status,
                        order.expiresAt
                ))
                .from(order)
                .join(member).on(member.id.eq(order.memberId))
                .where(
                        order.orderKey.eq(orderKey),
                        order.memberId.eq(memberId),
                        member.deletedAt.isNull()
                )
                .fetchOne());
    }
}
