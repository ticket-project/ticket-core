package com.ticket.core.app.order.query;

import com.ticket.support.error.CoreException;
import com.ticket.core.domain.error.DomainErrorType;
import com.ticket.core.domain.order.model.Order;
import com.ticket.core.app.order.query.OrderFinder;
import com.ticket.core.domain.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class OrderFinderTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderFinder orderFinder;

    @Test
    void 주문키와_회원으로_소유한_주문을_찾아_반환한다() {
        //given
        Order order = createOrder();
        when(orderRepository.findByOrderKeyAndMemberId("order-key", 1L)).thenReturn(Optional.of(order));

        //when
        Order result = orderFinder.findOwnedByOrderKey("order-key", 1L);

        //then
        assertThat(result).isSameAs(order);
    }

    @Test
    void 소유한_주문이_없으면_ORDER_NOT_OWNED_예외를_던진다() {
        //given
        when(orderRepository.findByOrderKeyAndMemberId("missing", 1L)).thenReturn(Optional.empty());

        //when
        //then
        assertThatThrownBy(() -> orderFinder.findOwnedByOrderKey("missing", 1L))
                .isInstanceOf(CoreException.class)
                .satisfies(thrown -> assertThat(((CoreException) thrown).getErrorType()).isEqualTo(DomainErrorType.ORDER_NOT_OWNED));
    }

    @Test
    void 대기중인_소유_주문을_잠금조회로_찾아_반환한다() {
        //given
        Order order = createOrder();
        when(orderRepository.findByOrderKeyAndMemberIdForUpdate("order-key", 1L)).thenReturn(Optional.of(order));

        //when
        Order result = orderFinder.findPendingOwnedByOrderKeyForUpdate("order-key", 1L);

        //then
        assertThat(result).isSameAs(order);
    }

    @Test
    void 잠금조회한_주문이_없으면_ORDER_NOT_OWNED_예외를_던진다() {
        //given
        when(orderRepository.findByOrderKeyAndMemberIdForUpdate("missing", 1L)).thenReturn(Optional.empty());

        //when
        //then
        assertThatThrownBy(() -> orderFinder.findPendingOwnedByOrderKeyForUpdate("missing", 1L))
                .isInstanceOf(CoreException.class)
                .satisfies(thrown -> assertThat(((CoreException) thrown).getErrorType()).isEqualTo(DomainErrorType.ORDER_NOT_OWNED));
    }

    @Test
    void 잠금조회한_주문이_pending이_아니면_ORDER_NOT_PENDING_예외를_던진다() {
        //given
        Order order = createOrder();
        order.confirm(LocalDateTime.of(2026, 3, 15, 12, 0));
        when(orderRepository.findByOrderKeyAndMemberIdForUpdate("order-key", 1L)).thenReturn(Optional.of(order));

        //when
        //then
        assertThatThrownBy(() -> orderFinder.findPendingOwnedByOrderKeyForUpdate("order-key", 1L))
                .isInstanceOf(CoreException.class)
                .satisfies(thrown -> assertThat(((CoreException) thrown).getErrorType()).isEqualTo(DomainErrorType.ORDER_NOT_PENDING));
    }

    private Order createOrder() {
        return new Order(
                1L,
                10L,
                "order-key",
                "hold-key",
                BigDecimal.valueOf(15000),
                LocalDateTime.of(2026, 3, 15, 12, 30)
        );
    }
}
