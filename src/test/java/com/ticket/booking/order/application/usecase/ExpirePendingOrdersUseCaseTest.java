package com.ticket.booking.order.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticket.booking.order.domain.Order;
import com.ticket.booking.order.domain.OrderRepository;
import com.ticket.booking.order.domain.OrderState;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class ExpirePendingOrdersUseCaseTest {
    private static final int BATCH_SIZE = 100;
    private static final LocalDateTime EXPECTED_NOW = LocalDateTime.of(2026, 3, 15, 10, 0);
    @Mock private OrderRepository orderRepository;
    @Mock private ExpireOrderUseCase expireOrderUseCase;
    private final Clock fixedClock =
            Clock.fixed(Instant.parse("2026-03-15T01:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Test
    void 만료_대상이_없으면_바로_끝난다() {
        when(orderRepository.findExpirable(
                        eq(OrderState.PENDING), eq(EXPECTED_NOW), isNull(), anyInt()))
                .thenReturn(List.<Order>of());

        final ExpirePendingOrdersUseCase.Output output = useCase().execute();

        assertThat(output).isEqualTo(new ExpirePendingOrdersUseCase.Output(0, 0));
        verify(expireOrderUseCase, times(0)).expireByOrderId(any(), any());
    }

    @Test
    void 만료_대상을_clock의_현재_시각으로_만료한다() {
        final List<Order> page = List.of(order(1L, null), order(2L, null));
        when(orderRepository.findExpirable(
                        eq(OrderState.PENDING), eq(EXPECTED_NOW), isNull(), anyInt()))
                .thenReturn(page);

        final ExpirePendingOrdersUseCase.Output output = useCase().execute();

        assertThat(output).isEqualTo(new ExpirePendingOrdersUseCase.Output(2, 0));
        verify(expireOrderUseCase).expireByOrderId(1L, EXPECTED_NOW);
        verify(expireOrderUseCase).expireByOrderId(2L, EXPECTED_NOW);
        // 마지막 페이지가 BATCH_SIZE 미만이면 더 읽지 않는다.
        verify(orderRepository, times(1))
                .findExpirable(eq(OrderState.PENDING), eq(EXPECTED_NOW), isNull(), anyInt());
    }

    /**
     * 재현한 결함: 앞의 한 페이지가 전부 실패하면 옛 구현은 같은 첫 페이지를 다시 읽게 되므로 무한 반복을 피하려고 순회를 중단했고, 그 뒤의 정상 만료 대상은 영원히
     * 처리되지 않았다. 커서가 실패 항목을 넘어가므로 뒤의 대상이 같은 순회에서 처리돼야 한다.
     */
    @Test
    void 앞_페이지가_전부_실패해도_뒤의_대상을_계속_처리한다() {
        final List<Order> failingPage = orders(1L, BATCH_SIZE);
        final List<Order> healthyPage = orders(BATCH_SIZE + 1L, 2);
        when(orderRepository.findExpirable(
                        eq(OrderState.PENDING), eq(EXPECTED_NOW), isNull(), eq(BATCH_SIZE)))
                .thenReturn(failingPage);
        when(orderRepository.findExpirable(
                        eq(OrderState.PENDING),
                        eq(EXPECTED_NOW),
                        eq((long) BATCH_SIZE),
                        eq(BATCH_SIZE)))
                .thenReturn(healthyPage);
        // 스터빙 중에 mock의 메서드를 호출하지 않도록 id를 먼저 꺼내 둔다.
        final List<Long> failingIds = failingPage.stream().map(Order::getId).toList();
        failingIds.forEach(
                id ->
                        doThrow(new RuntimeException("boom"))
                                .when(expireOrderUseCase)
                                .expireByOrderId(id, EXPECTED_NOW));

        final ExpirePendingOrdersUseCase.Output output = useCase().execute();

        assertThat(output).isEqualTo(new ExpirePendingOrdersUseCase.Output(2, BATCH_SIZE));
        verify(expireOrderUseCase).expireByOrderId(BATCH_SIZE + 1L, EXPECTED_NOW);
        verify(expireOrderUseCase).expireByOrderId(BATCH_SIZE + 2L, EXPECTED_NOW);
    }

    /** 실패 항목은 삭제하지도 처리 완료로 치지도 않는다 — PENDING으로 남아 다음 순회에서 다시 시도된다. */
    @Test
    void 실패_항목은_처리_완료로_세지_않는다() {
        final Order order = order(1L, "order-1");
        when(orderRepository.findExpirable(
                        eq(OrderState.PENDING), eq(EXPECTED_NOW), isNull(), anyInt()))
                .thenReturn(List.of(order));
        doThrow(new RuntimeException("boom"))
                .when(expireOrderUseCase)
                .expireByOrderId(1L, EXPECTED_NOW);

        final ExpirePendingOrdersUseCase.Output output = useCase().execute();

        assertThat(output).isEqualTo(new ExpirePendingOrdersUseCase.Output(0, 1));
        verify(expireOrderUseCase).expireByOrderId(1L, EXPECTED_NOW);
    }

    /** 커서가 앞으로만 가므로 같은 페이지를 다시 읽지 않는다 — 빈 페이지를 만나면 끝난다. */
    @Test
    void 가득_찬_페이지_뒤에_빈_페이지가_오면_순회가_끝난다() {
        final List<Order> fullPage = orders(1L, BATCH_SIZE);
        when(orderRepository.findExpirable(
                        eq(OrderState.PENDING), eq(EXPECTED_NOW), isNull(), eq(BATCH_SIZE)))
                .thenReturn(fullPage);
        when(orderRepository.findExpirable(
                        eq(OrderState.PENDING),
                        eq(EXPECTED_NOW),
                        eq((long) BATCH_SIZE),
                        eq(BATCH_SIZE)))
                .thenReturn(List.of());

        final ExpirePendingOrdersUseCase.Output output = useCase().execute();

        assertThat(output).isEqualTo(new ExpirePendingOrdersUseCase.Output(BATCH_SIZE, 0));
        verify(orderRepository, times(2))
                .findExpirable(eq(OrderState.PENDING), eq(EXPECTED_NOW), any(), eq(BATCH_SIZE));
    }

    private ExpirePendingOrdersUseCase useCase() {
        return new ExpirePendingOrdersUseCase(orderRepository, expireOrderUseCase, fixedClock);
    }

    private List<Order> orders(final long firstId, final int count) {
        final List<Order> orders = new ArrayList<>();
        IntStream.range(0, count).forEach(index -> orders.add(order(firstId + index, null)));
        return orders;
    }

    private Order order(final Long id, final String orderKey) {
        final Order order = mock(Order.class);
        when(order.getId()).thenReturn(id);
        if (orderKey != null) {
            when(order.getOrderKey()).thenReturn(orderKey);
        }
        return order;
    }
}
