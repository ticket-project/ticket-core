package com.ticket.booking.internal.infrastructure.order.query;

import com.ticket.booking.internal.application.order.query.OrderReadRepository;
import com.ticket.identity.internal.domain.member.model.Member;
import com.ticket.booking.internal.domain.order.model.Order;
import com.ticket.booking.internal.domain.order.model.OrderSeat;
import com.ticket.booking.internal.domain.order.model.OrderState;
import com.ticket.booking.internal.application.order.query.model.OrderDetailRow;
import com.ticket.booking.internal.application.order.query.model.OrderStatusView;
import com.ticket.catalog.internal.domain.performance.Performance;
import com.ticket.booking.internal.domain.performanceseat.model.PerformanceSeat;
import com.ticket.booking.internal.domain.performanceseat.model.PerformanceSeatState;
import com.ticket.catalog.internal.domain.seat.Seat;
import com.ticket.catalog.internal.domain.show.Region;
import com.ticket.catalog.internal.domain.show.Show;
import com.ticket.catalog.internal.domain.show.Venue;
import com.ticket.core.infra.support.ReadRepositoryTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import(QuerydslOrderReadRepository.class)
@SuppressWarnings("NonAsciiCharacters")
class QuerydslOrderReadRepositoryTest extends ReadRepositoryTestSupport {

    @Autowired
    private OrderReadRepository repository;

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
    void 활성_회원의_주문상태를_조회한다() {
        OrderStatusView status = repository.findStatus(orderKey, memberId).orElseThrow();

        assertThat(status.orderKey()).isEqualTo(orderKey);
        assertThat(status.status()).isEqualTo(OrderState.PENDING);
    }

    @Test
    void 탈퇴_회원의_주문상태는_조회하지_않는다() {
        Member member = entityManager.find(Member.class, memberId);
        member.withdraw(LocalDateTime.now(clock));
        flushAndClear();

        assertThat(repository.findStatus(orderKey, memberId)).isEmpty();
    }
}
