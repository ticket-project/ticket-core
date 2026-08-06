package com.ticket.core.domain.order.query;

import com.ticket.core.domain.member.model.Member;
import com.ticket.core.domain.order.model.Order;
import com.ticket.core.domain.order.model.OrderSeat;
import com.ticket.core.domain.order.model.OrderState;
import com.ticket.core.domain.order.query.model.OrderDetailRow;
import com.ticket.core.domain.order.query.model.OrderStatusView;
import com.ticket.core.domain.performance.model.Performance;
import com.ticket.core.domain.performanceseat.model.PerformanceSeat;
import com.ticket.core.domain.performanceseat.model.PerformanceSeatState;
import com.ticket.core.domain.seat.model.Seat;
import com.ticket.core.domain.show.meta.Region;
import com.ticket.core.domain.show.model.Show;
import com.ticket.core.domain.show.venue.Venue;
import com.ticket.core.domain.support.QueryRepositoryTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import(OrderQueryRepository.class)
@SuppressWarnings("NonAsciiCharacters")
class OrderQueryRepositoryTest extends QueryRepositoryTestSupport {

    @Autowired
    private OrderQueryRepository repository;

    private Long memberId;
    private String orderKey;

    @BeforeEach
    void setUp() throws Exception {
        Member member = persistMember("user@example.com", "홍길동");
        Venue venue = persistVenue("올림픽홀", Region.SEOUL);
        Show show = persistShow(
                "뮤지컬",
                venue,
                null,
                0L,
                LocalDateTime.now(clock).minusDays(1),
                LocalDateTime.now(clock).plusDays(1)
        );
        Performance performance = persistPerformance(show, 1L, LocalDateTime.now(clock).plusDays(1));
        Seat seat = persistSeat("A", "10", "7", 1);
        PerformanceSeat performanceSeat = persistPerformanceSeat(
                performance,
                seat,
                PerformanceSeatState.AVAILABLE,
                BigDecimal.valueOf(120000)
        );
        Order order = new Order(
                member.getId(),
                performance.getId(),
                "order-key",
                "hold-key",
                BigDecimal.valueOf(120000),
                LocalDateTime.now(clock).plusMinutes(10)
        );
        entityManager.persist(order);
        entityManager.persist(new OrderSeat(
                order,
                performanceSeat.getId(),
                seat.getId(),
                BigDecimal.valueOf(120000)
        ));
        memberId = member.getId();
        orderKey = order.getOrderKey();
        flushAndClear();
    }

    @Test
    void 주문상세에_필요한_행을_한번에_조회한다() {
        List<OrderDetailRow> rows = repository.findDetailRows(orderKey, memberId);

        assertThat(rows).hasSize(1);
        OrderDetailRow row = rows.getFirst();
        assertThat(row.showTitle()).isEqualTo("뮤지컬");
        assertThat(row.venueName()).isEqualTo("올림픽홀");
        assertThat(row.memberEmail()).isEqualTo("user@example.com");
        assertThat(row.price()).isEqualByComparingTo("120000");
    }

    @Test
    void 주문상태는_주문_컬럼만_조회한다() {
        OrderStatusView status = repository.findStatus(orderKey, memberId).orElseThrow();

        assertThat(status.orderKey()).isEqualTo(orderKey);
        assertThat(status.status()).isEqualTo(OrderState.PENDING);
    }
}
