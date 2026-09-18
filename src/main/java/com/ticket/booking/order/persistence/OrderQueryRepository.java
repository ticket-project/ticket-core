package com.ticket.booking.order.query;

import static com.ticket.booking.order.domain.QOrder.order;
import static com.ticket.booking.order.domain.QOrderSeat.orderSeat;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

/**
 * booking이 소유한 order/orderSeat 테이블만 조회한다. show/member 표시값은 여기서 조회하지 않는다 — {@code
 * GetOrderDetailUseCase}/{@code GetOrderStatusUseCase}가 그 module들의 공개 API를 호출해 합성한다.
 */
@Repository
@RequiredArgsConstructor
public class OrderQuery {
    private final JPAQueryFactory queryFactory;

    public List<OrderDetailRow> findDetailRows(final String orderKey, final Long memberId) {
        return queryFactory
                .select(
                        Projections.constructor(
                                OrderDetailRow.class,
                                order.orderKey,
                                order.status,
                                order.expiresAt,
                                order.memberId,
                                order.performanceId,
                                order.showTitleSnapshot,
                                order.performanceStartAtSnapshot,
                                order.venueNameSnapshot,
                                orderSeat.performanceSeatId,
                                orderSeat.seatId,
                                orderSeat.unitPrice,
                                orderSeat.gradeCodeSnapshot,
                                orderSeat.gradeNameSnapshot,
                                orderSeat.seatLabelSnapshot))
                .from(order)
                .join(orderSeat)
                .on(orderSeat.order.id.eq(order.id))
                .where(order.orderKey.eq(orderKey), order.memberId.eq(memberId))
                .orderBy(orderSeat.id.asc())
                .fetch();
    }

    public Optional<OrderStatusView> findStatus(final String orderKey, final Long memberId) {
        return Optional.ofNullable(
                queryFactory
                        .select(
                                Projections.constructor(
                                        OrderStatusView.class,
                                        order.orderKey,
                                        order.status,
                                        order.expiresAt))
                        .from(order)
                        .where(order.orderKey.eq(orderKey), order.memberId.eq(memberId))
                        .fetchOne());
    }
}
