package com.ticket.booking.order.usecase;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.ticket.booking.order.usecase.ExpireOrderUseCase.ExpiredOrder;
import com.ticket.booking.order.usecase.ExpireOrderUseCase.OrderExpirationFailedException;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

/**
 * 만료 시각이 지난 PENDING 주문을 {@code EXPIRED}로 바꾸는 청소다. 주문 만료의 유일한 경로다(Redis 만료 알림은 쓰지 않는다).
 *
 * <p><b>모든 인스턴스가 함께 돈다.</b> 주문 한 건마다 트랜잭션을 열어 다른 인스턴스가 잠그지 않은 만료 주문 하나를 {@code FOR UPDATE SKIP LOCKED}로 잡는다. 같은 주문을 두 번
 * 처리하지 않고, 인스턴스 하나가 죽으면 DB가 롤백하며 잠금을 풀어 남은 인스턴스가 다음 조회에서 잡는다.
 *
 * <p><b>실패한 주문을 넘어 뒤의 주문까지 처리한다.</b> 실패한 주문은 롤백으로 다시 첫 대상이 되므로 id 커서로 지나간다. PENDING으로 남아 다음 순회에서 다시 시도된다. id를 모르는 실패(커밋
 * 실패 등 DB 쪽 문제)는 이번 순회를 멈춘다.
 *
 * <p>순회당 상한은 두지 않는다. 한 건씩 처리해 인스턴스마다 메모리에는 주문 한 건, DB 연결은 하나만 쓰고 트랜잭션도 짧다.
 *
 * <p>지표: {@code booking.order.expiration.delay}는 만료 시각 뒤 몇 초 만에 순회가 그 주문을 잡았는지다(청소 주기가 충분한지 본다).
 * {@code booking.order.expiration.failures}는 다음 순회로 미룬 건수다. 임계값은 부하 측정 뒤에 정한다(스케일 아웃 결정 D2-2).
 */
@Slf4j
@Service
public class ExpirePendingOrdersUseCase {
    private final ExpireOrderUseCase expireOrderUseCase;
    private final Clock clock;
    private final Timer delay;
    private final Counter failures;

    public ExpirePendingOrdersUseCase(
            final ExpireOrderUseCase expireOrderUseCase, final Clock clock, final MeterRegistry meterRegistry) {
        this.expireOrderUseCase = expireOrderUseCase;
        this.clock = clock;
        this.delay = Timer.builder("booking.order.expiration.delay")
                .description("주문 만료 시각 뒤 청소가 그 주문을 잡기까지 걸린 시간")
                .register(meterRegistry);
        this.failures = Counter.builder("booking.order.expiration.failures")
                .description("주문 만료 청소에서 실패해 다음 순회로 미룬 건수")
                .register(meterRegistry);
    }

    /**
     * @param processedCount 이번 순회에서 만료시킨 건수
     * @param failedCount 이번 순회에서 실패해 다음 순회로 미룬 건수
     */
    public record Output(int processedCount, int failedCount) {}

    public Output execute() {
        final LocalDateTime now = LocalDateTime.now(clock);
        int processed = 0;
        int failed = 0;
        long cursor = 0L;

        while (true) {
            final Optional<ExpiredOrder> expired;
            try {
                expired = expireOrderUseCase.expireNext(now, cursor);
            } catch (final OrderExpirationFailedException e) {
                log.error("주문 만료 처리 실패: orderId={}", e.orderId(), e);
                cursor = e.orderId();
                failed++;
                failures.increment();
                continue;
            } catch (final RuntimeException e) {
                log.error("주문 만료 순회를 멈춥니다. cursor={}", cursor, e);
                failed++;
                failures.increment();
                break;
            }
            if (expired.isEmpty()) {
                break;
            }
            cursor = expired.get().orderId();
            delay.record(Duration.between(expired.get().expiresAt(), now));
            processed++;
        }

        if (failed > 0) {
            log.warn("주문 만료 순회에서 일부가 실패해 다음 순회로 미룹니다. processedCount={}, failedCount={}", processed, failed);
        }
        return new Output(processed, failed);
    }
}
