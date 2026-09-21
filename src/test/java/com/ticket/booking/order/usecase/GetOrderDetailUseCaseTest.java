package com.ticket.booking.order.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticket.booking.exception.OrderNotOwnedException;
import com.ticket.booking.order.domain.Order;
import com.ticket.booking.order.domain.OrderRepository;
import com.ticket.member.api.MemberLookupApi;
import com.ticket.member.api.MemberSnapshot;
import com.ticket.shared.exception.NotFoundException;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class GetOrderDetailUseCaseTest {
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-03-15T10:00:00Z"), ZoneId.of("Asia/Seoul"));
    @Mock private OrderRepository orderRepository;
    @Mock private MemberLookupApi memberLookup;
    private GetOrderDetailUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetOrderDetailUseCase(orderRepository, memberLookup, FIXED_CLOCK);
    }

    @Test
    void 단일_조회결과를_주문상세로_조합한다() {
        when(orderRepository.findDetailByOrderKeyAndMemberId("order-key", 1L))
                .thenReturn(Optional.of(order()));
        when(memberLookup.getProfile(1L))
                .thenReturn(new MemberSnapshot(1L, "홍길동", "user@example.com"));

        GetOrderDetailUseCase.Output output =
                useCase.execute(new GetOrderDetailUseCase.Input("order-key", 1L));

        assertThat(output.orderKey()).isEqualTo("order-key");
        assertThat(output.show().title()).isEqualTo("뮤지컬");
        assertThat(output.performance().venueName()).isEqualTo("올림픽홀");
        assertThat(output.booker().email()).isEqualTo("user@example.com");
        assertThat(output.price().ticketAmount()).isEqualByComparingTo("120000");
        assertThat(output.tickets().count()).isEqualTo(1);
        assertThat(output.tickets().seats().getFirst().label()).isEqualTo("1F A구역 10열 7번");
        assertThat(output.tickets().seats().getFirst().gradeCode()).isEqualTo("VIP");
        assertThat(output.remainingSeconds()).isEqualTo(600L);
    }

    /** 좌석 목록의 순서와 합계는 Order가 가진 좌석 순서를 그대로 따른다. */
    @Test
    void 여러_좌석은_주문이_가진_순서대로_담고_단가를_더한다() {
        Order order = order();
        order.addOrderSeat(502L, 43L, BigDecimal.valueOf(100000), "R", "R석", "1F A구역 10열 8번");
        when(orderRepository.findDetailByOrderKeyAndMemberId("order-key", 1L))
                .thenReturn(Optional.of(order));
        when(memberLookup.getProfile(1L))
                .thenReturn(new MemberSnapshot(1L, "홍길동", "user@example.com"));

        GetOrderDetailUseCase.Output output =
                useCase.execute(new GetOrderDetailUseCase.Input("order-key", 1L));

        assertThat(output.tickets().count()).isEqualTo(2);
        assertThat(output.tickets().seats())
                .extracting(GetOrderDetailUseCase.TicketSeat::performanceSeatId)
                .containsExactly(501L, 502L);
        assertThat(output.price().ticketAmount()).isEqualByComparingTo("220000");
        assertThat(output.price().totalAmount()).isEqualByComparingTo("220000");
    }

    @Test
    void show_값이_바뀌어도_이미_만든_주문_상세는_바뀌지_않는다() {
        // Order/OrderSeat가 생성 시점에 남긴 snapshot만 쓰므로 show를 다시 조회하지 않는다.
        when(orderRepository.findDetailByOrderKeyAndMemberId("order-key", 1L))
                .thenReturn(Optional.of(order()));
        when(memberLookup.getProfile(1L))
                .thenReturn(new MemberSnapshot(1L, "홍길동", "user@example.com"));

        GetOrderDetailUseCase.Output output =
                useCase.execute(new GetOrderDetailUseCase.Input("order-key", 1L));

        assertThat(output.show().title()).isEqualTo("뮤지컬");
        assertThat(output.tickets().seats().getFirst().price()).isEqualByComparingTo("120000");
    }

    @Test
    void 본인_주문이_없으면_권한예외를_던진다() {
        when(orderRepository.findDetailByOrderKeyAndMemberId("missing", 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new GetOrderDetailUseCase.Input("missing", 1L)))
                .isInstanceOf(OrderNotOwnedException.class)
                .hasFieldOrPropertyWithValue("orderKey", "missing")
                .hasFieldOrPropertyWithValue("memberId", 1L);

        verifyNoInteractions(memberLookup);
    }

    @Test
    void 탈퇴한_회원의_주문이면_조회하지_않는다() {
        when(orderRepository.findDetailByOrderKeyAndMemberId("order-key", 1L))
                .thenReturn(Optional.of(order()));
        when(memberLookup.getProfile(1L)).thenThrow(new NotFoundException());

        assertThatThrownBy(() -> useCase.execute(new GetOrderDetailUseCase.Input("order-key", 1L)))
                .isInstanceOf(NotFoundException.class);
    }

    private Order order() {
        Order order =
                new Order(
                        1L,
                        10L,
                        "order-key",
                        "hold-key",
                        LocalDateTime.of(2026, 3, 15, 19, 10),
                        "뮤지컬",
                        LocalDateTime.of(2026, 3, 20, 19, 30),
                        "올림픽홀");
        order.addOrderSeat(501L, 42L, BigDecimal.valueOf(120000), "VIP", "VIP", "1F A구역 10열 7번");
        return order;
    }
}
