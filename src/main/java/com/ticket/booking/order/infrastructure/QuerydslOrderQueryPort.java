package com.ticket.booking.order.infrastructure;

import com.ticket.booking.order.application.usecase.GetOrderDetailUseCase;
import com.ticket.booking.order.application.usecase.GetOrderStatusUseCase;

import com.ticket.booking.order.application.port.OrderQueryPort;

import com.ticket.booking.order.application.port.OrderQueryPort;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.booking.order.application.OrderDetailRow;
import com.ticket.booking.order.application.OrderStatusView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.ticket.booking.order.domain.QOrder.order;
import static com.ticket.booking.order.domain.QOrderSeat.orderSeat;

/**
 * booking이 소유한 order/orderSeat 테이블만 조회한다. show/member 표시값은 여기서 조회하지
 * 않는다 — {@code GetOrderDetailUseCase}/{@code GetOrderStatusUseCase}가 그 module들의 공개 API를
 * 호출해 합성한다.
 */
@Repository
@RequiredArgsConstructor
public class QuerydslOrderQueryPort implements OrderQueryPort {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<OrderDetailRow> findDetailRows(final String orderKey, final Long memberId) {
        return queryFactory
                .select(Projections.constructor(OrderDetailRow.class,
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
                        orderSeat.seatLabelSnapshot
                ))
                .from(order)
                .join(orderSeat).on(orderSeat.order.id.eq(order.id))
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
                .where(
                        order.orderKey.eq(orderKey),
                        order.memberId.eq(memberId)
                )
                .fetchOne());
    }
}
