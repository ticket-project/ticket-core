package com.ticket.booking.internal.infrastructure.order.query;

import com.ticket.booking.internal.application.order.query.OrderReadRepository;
import com.ticket.booking.internal.domain.order.model.Order;
import com.ticket.booking.internal.domain.order.model.OrderSeat;
import com.ticket.booking.internal.domain.order.model.OrderState;
import com.ticket.booking.internal.application.order.query.model.OrderDetailRow;
import com.ticket.booking.internal.application.order.query.model.OrderStatusView;
import com.ticket.core.infra.support.ReadRepositoryTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * booking이 소유한 order/orderSeat 테이블만으로 조회하는지 확인한다. catalog/identity 표시값
 * 합성은 {@code GetOrderDetailUseCase}/{@code GetOrderStatusUseCase} 단위 테스트가 담당한다.
 */
@Import(QuerydslOrderReadRepository.class)
@SuppressWarnings("NonAsciiCharacters")
class QuerydslOrderReadRepositoryTest extends ReadRepositoryTestSupport {

    @Autowired
    private OrderReadRepository repository;

    private Long memberId;
    private Long performanceId;
    private String orderKey;

    @BeforeEach
    void setUp() {
        memberId = 1L;
        performanceId = 10L;
        Order order = new Order(
                memberId,
                performanceId,
                "order-key",
                "hold-key",
                BigDecimal.valueOf(120000),
                LocalDateTime.now(clock).plusMinutes(10)
        );
        entityManager.persist(order);
        entityManager.persist(new OrderSeat(
                order,
                501L,
                42L,
                BigDecimal.valueOf(120000)
        ));
        orderKey = order.getOrderKey();
        flushAndClear();
    }

    @Test
    void 주문상세에_필요한_행을_한번에_조회한다() {
        List<OrderDetailRow> rows = repository.findDetailRows(orderKey, memberId);

        assertThat(rows).hasSize(1);
        OrderDetailRow row = rows.getFirst();
        assertThat(row.memberId()).isEqualTo(memberId);
        assertThat(row.performanceId()).isEqualTo(performanceId);
        assertThat(row.performanceSeatId()).isEqualTo(501L);
        assertThat(row.seatId()).isEqualTo(42L);
        assertThat(row.price()).isEqualByComparingTo("120000");
    }

    @Test
    void 다른_회원의_주문키로는_조회되지_않는다() {
        assertThat(repository.findDetailRows(orderKey, memberId + 1)).isEmpty();
        assertThat(repository.findStatus(orderKey, memberId + 1)).isEmpty();
    }

    @Test
    void 주문상태를_조회한다() {
        OrderStatusView status = repository.findStatus(orderKey, memberId).orElseThrow();

        assertThat(status.orderKey()).isEqualTo(orderKey);
        assertThat(status.status()).isEqualTo(OrderState.PENDING);
    }
}
