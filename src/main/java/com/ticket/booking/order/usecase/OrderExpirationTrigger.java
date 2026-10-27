package com.ticket.booking.order.usecase;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 주문 만료 청소를 주기적으로 깨운다(기본 5초). 모든 인스턴스에서 돈다 — 같은 주문은 {@code FOR UPDATE SKIP LOCKED}로 한 인스턴스만 처리한다.
 *
 * <p>여기에는 조회도 상태 판단도 두지 않는다. 실행 주기만 정하고 유스케이스를 한 번 부른다.
 */
@Component
@ConditionalOnProperty(prefix = "worker", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class OrderExpirationTrigger {
    private final ExpirePendingOrdersUseCase expirePendingOrdersUseCase;

    @Scheduled(fixedDelayString = "${worker.order-expiration.fixed-delay:5000}")
    public void run() {
        expirePendingOrdersUseCase.execute();
    }
}
