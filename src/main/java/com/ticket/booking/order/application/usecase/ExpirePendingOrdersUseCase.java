package com.ticket.booking.order.application.usecase;

import com.ticket.booking.order.domain.Order;
import com.ticket.booking.order.domain.OrderState;
import com.ticket.booking.order.domain.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 만료 시각이 지난 PENDING 주문을 보정 처리한다.
 *
 * <p>정상 경로는 Redis TTL 만료 리스너가 즉시 처리한다. 이 유스케이스는 그 실행을 놓친 주문을
 * 다시 만료시키는 보정 경로이며, 배치 반복과 실패 격리를 이 계층이 소유한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpirePendingOrdersUseCase {

    private static final int BATCH_SIZE = 100;

    private final OrderRepository orderRepository;
    private final ExpireOrderUseCase expireOrderUseCase;
    private final Clock clock;

    public record Output(int processedCount) {
    }

    public Output execute() {
        final LocalDateTime now = LocalDateTime.now(clock);
        int totalProcessed = 0;
        while (true) {
            final List<Order> expiredOrders = orderRepository.findExpirable(OrderState.PENDING, now, BATCH_SIZE);
            if (expiredOrders.isEmpty()) {
                return new Output(totalProcessed);
            }

            final int processedCount = expireEach(expiredOrders, now);
            totalProcessed += processedCount;
            if (shouldStop(expiredOrders, processedCount)) {
                return new Output(totalProcessed);
            }
        }
    }

    private int expireEach(final List<Order> expiredOrders, final LocalDateTime now) {
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

    /**
     * 한 건도 처리하지 못했으면 같은 페이지를 무한 반복하지 않도록 멈춘다.
     */
    private boolean shouldStop(final List<Order> expiredOrders, final int processedCount) {
        if (processedCount == 0) {
            log.warn("주문 만료 배치에서 처리 성공 건이 없어 반복을 중단합니다. pendingCount={}", expiredOrders.size());
            return true;
        }
        return expiredOrders.size() < BATCH_SIZE;
    }
}
