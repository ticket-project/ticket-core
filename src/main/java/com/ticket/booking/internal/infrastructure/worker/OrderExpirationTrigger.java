package com.ticket.booking.internal.infrastructure.worker;

import com.ticket.booking.internal.application.order.command.ExpirePendingOrdersUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 만료 보정 유스케이스를 주기적으로 깨운다.
 *
 * <p>여기에는 조회도 상태 판단도 두지 않는다. 실행 주기만 정하고 유스케이스를 한 번 부른다.
 */
@Component
@ConditionalOnProperty(prefix = "worker", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class OrderExpirationTrigger {

    private final ExpirePendingOrdersUseCase expirePendingOrdersUseCase;

    @Scheduled(fixedDelayString = "${worker.order-expiration.fixed-delay:300000}")
    public void run() {
        expirePendingOrdersUseCase.execute();
    }
}
