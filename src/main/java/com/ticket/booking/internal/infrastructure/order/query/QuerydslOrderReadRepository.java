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

import static com.ticket.booking.internal.domain.order.model.QOrder.order;
import static com.ticket.booking.internal.domain.order.model.QOrderSeat.orderSeat;

/**
 * booking이 소유한 order/orderSeat 테이블만 조회한다. catalog/member 표시값은 여기서 조회하지
 * 않는다 — {@code GetOrderDetailUseCase}/{@code GetOrderStatusUseCase}가 그 module들의 공개 API를
 * 호출해 합성한다.
 */
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
