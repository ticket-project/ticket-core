package com.ticket.core.infra.order;

import com.ticket.core.app.order.command.ExpireOrderUseCase;
import com.ticket.core.domain.order.model.Order;
import com.ticket.core.domain.order.model.OrderState;
import com.ticket.core.domain.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpirationScheduler {

    private static final int BATCH_SIZE = 100;

    private final ExpireOrderUseCase expireOrderUseCase;
    private final OrderRepository orderRepository;
    private final Clock clock;

    @Scheduled(fixedDelayString = "300000")
    public void expirePendingOrders() {
        final LocalDateTime now = LocalDateTime.now(clock);
        while (true) {
            final List<Order> expiredOrders =
                    orderRepository.findExpirable(OrderState.PENDING, now, BATCH_SIZE);
            if (expiredOrders.isEmpty()) {
                return;
            }

            final int processedCount = processBatch(expiredOrders, now);
            if (shouldStop(expiredOrders, processedCount)) {
                return;
            }
        }
    }

    private int processBatch(final List<Order> expiredOrders, final LocalDateTime now) {
        int processedCount = 0;
        for (final Order order : expiredOrders) {
            try {
                expireOrderUseCase.expireByOrderId(order.getId(), now);
                processedCount++;
            } catch (final RuntimeException e) {
                log.error("주문 만료 처리 실패: orderKey={}, orderId={}", order.getOrderKey(), order.getId(), e);
            }
        }
        return processedCount;
    }

    private boolean shouldStop(final List<Order> expiredOrders, final int processedCount) {
        if (processedCount == 0) {
            log.warn("주문 만료 배치에서 처리 성공 건이 없어 반복을 중단합니다. pendingCount={}",
                    expiredOrders.size());
            return true;
        }
        return expiredOrders.size() < BATCH_SIZE;
    }
}
