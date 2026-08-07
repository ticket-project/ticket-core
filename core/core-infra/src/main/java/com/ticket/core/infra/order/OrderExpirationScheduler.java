package com.ticket.core.infra.order;

import com.ticket.core.domain.order.command.expire.ExpireOrderUseCase;
import com.ticket.core.domain.order.model.Order;
import com.ticket.core.domain.order.repository.OrderRepository;
import com.ticket.core.domain.order.model.OrderState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

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
            final Slice<Order> expiredOrders = loadBatch(now);
            if (!expiredOrders.hasContent()) {
                return;
            }

            final int processedCount = processBatch(expiredOrders, now);
            if (shouldStop(expiredOrders, processedCount)) {
                return;
            }
        }
    }

    private Slice<Order> loadBatch(final LocalDateTime now) {
        return orderRepository.findAllByStatusAndExpiresAtLessThanEqual(
                OrderState.PENDING,
                now,
                PageRequest.of(0, BATCH_SIZE, Sort.by(Sort.Direction.ASC, "id"))
        );
    }

    private int processBatch(final Slice<Order> expiredOrders, final LocalDateTime now) {
        int processedCount = 0;
        for (final Order order : expiredOrders.getContent()) {
            try {
                expireOrderUseCase.expireByOrderId(order.getId(), now);
                processedCount++;
            } catch (final RuntimeException e) {
                log.error("주문 만료 처리 실패: orderKey={}, orderId={}", order.getOrderKey(), order.getId(), e);
            }
        }
        return processedCount;
    }

    private boolean shouldStop(final Slice<Order> expiredOrders, final int processedCount) {
        if (processedCount == 0) {
            log.warn("주문 만료 배치에서 처리 성공 건이 없어 반복을 중단합니다. pendingCount={}",
                    expiredOrders.getNumberOfElements());
            return true;
        }
        return expiredOrders.getNumberOfElements() < BATCH_SIZE;
    }
}
