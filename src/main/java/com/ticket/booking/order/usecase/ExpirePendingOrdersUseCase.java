package com.ticket.booking.order.usecase;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.ticket.booking.order.domain.Order;
import com.ticket.booking.order.domain.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 만료 시각이 지난 PENDING 주문을 만료시킨다.
 *
 * <p>주문 만료의 유일한 경로다. Redis 만료 알림은 쓰지 않는다 — 인스턴스가 여러 대면 모두가 같은 알림을 받기 때문이다. {@code OrderExpirationTrigger}가 1초마다 부르고, 배치
 * 반복과 실패 격리를 이 계층이 소유한다.
 *
 * <p><b>실패한 항목을 넘어 뒤의 대상까지 처리한다.</b> 옛 구현은 매번 첫 페이지를 다시 읽어, 앞의 100건이 계속 실패하면 그 뒤의 정상 만료 대상이 영원히 처리되지 않았다(한 건도 성공하지 못하면
 * 무한 반복을 피하려고 아예 멈췄다). 지금은 id 커서로 한 번의 순회에서 실패 항목을 지나쳐 다음 페이지로 나아간다. 실패 항목은 삭제하지도 처리 완료로 치지도 않으므로 PENDING으로 남아 다음 순회에서
 * 다시 시도된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpirePendingOrdersUseCase {
    private static final int BATCH_SIZE = 100;
    private final OrderRepository orderRepository;
    private final ExpireOrderUseCase expireOrderUseCase;
    private final Clock clock;

    /**
     * @param processedCount 이번 순회에서 예외 없이 끝난 건수. 그사이 다른 경로가 먼저 종료해 건너뛴 주문도 포함한다
     * @param failedCount 이번 순회에서 실패해 다음 순회로 미룬 건수
     */
    public record Output(int processedCount, int failedCount) {}

    public Output execute() {
        final LocalDateTime now = LocalDateTime.now(clock);
        int totalProcessed = 0;
        int totalFailed = 0;
        Long cursor = null;

        while (true) {
            final List<Order> expirableOrders = orderRepository.findExpirable(now, cursor, BATCH_SIZE);
            if (expirableOrders.isEmpty()) {
                break;
            }

            // 커서는 조회 결과의 마지막 id다. 처리 성공 여부와 무관하게 앞으로만 간다 — 이것이
            // 실패 항목을 넘어 뒤의 대상까지 처리하게 하는 지점이다. id는 불변·유일하므로 같은
            // 페이지를 다시 읽지 않는다.
            cursor = expirableOrders.getLast().getId();
            final int processedCount = expireEach(expirableOrders, now);
            totalProcessed += processedCount;
            totalFailed += expirableOrders.size() - processedCount;

            if (expirableOrders.size() < BATCH_SIZE) {
                break;
            }
        }

        if (totalFailed > 0) {
            log.warn(
                    "주문 만료 배치에서 일부 항목이 실패해 다음 순회로 미룹니다. processedCount={}, failedCount={}",
                    totalProcessed,
                    totalFailed);
        }
        return new Output(totalProcessed, totalFailed);
    }

    private int expireEach(final List<Order> expirableOrders, final LocalDateTime now) {
        int processedCount = 0;
        for (final Order order : expirableOrders) {
            try {
                expireOrderUseCase.expireByOrderId(order.getId(), now);
                processedCount++;
            } catch (final RuntimeException e) {
                log.error("주문 만료 처리 실패: orderKey={}, orderId={}", order.getOrderKey(), order.getId(), e);
            }
        }
        return processedCount;
    }
}
