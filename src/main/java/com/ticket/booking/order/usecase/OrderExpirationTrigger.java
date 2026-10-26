package com.ticket.booking.order.usecase;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * 주문 만료 유스케이스를 주기적으로 깨운다. 주문 만료는 Redis 만료 알림 없이 이 작업이 처리한다.
 *
 * <p>여기에는 조회도 상태 판단도 두지 않는다. 실행 주기만 정하고 유스케이스를 한 번 부른다.
 */
@Component
@ConditionalOnProperty(prefix = "worker", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class OrderExpirationTrigger {
    private final ExpirePendingOrdersUseCase expirePendingOrdersUseCase;

    /**
     * 인스턴스가 여러 대여도 한 대만 실행한다. 락을 최소 4초 쥐어, 주기(5초)가 조금 어긋난 다른 인스턴스가 같은 주기에 다시 실행하지 않게 한다. 한 번에 1분을 넘기면 락이 풀려 다른 인스턴스가
     * 시작할 수 있지만, 주문별 행 락과 PENDING 확인 때문에 같은 주문을 두 번 만료시키지는 않는다.
     */
    @Scheduled(fixedDelayString = "${worker.order-expiration.fixed-delay:5000}")
    @SchedulerLock(name = "orderExpiration", lockAtMostFor = "PT1M", lockAtLeastFor = "PT4S")
    public void run() {
        expirePendingOrdersUseCase.execute();
    }
}
