package com.ticket.booking.application;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.booking.OrderStarted;
import com.ticket.booking.domain.hold.Hold;
import com.ticket.booking.domain.order.Order;
import com.ticket.booking.domain.order.OrderRepository;
import com.ticket.booking.domain.seat.PerformanceSeat;
import com.ticket.show.api.PerformanceSaleSnapshot;

import lombok.RequiredArgsConstructor;

/**
 * 주문 생성의 booking local DB 쓰기 트랜잭션이다. 주문·주문 좌석·선점 이력·{@code OrderStarted} publication이 이 한 트랜잭션에서 함께
 * 커밋되거나 함께 사라진다.
 *
 * <p>조립({@link OrderCreator})과 저장을 나눠, 저장 책임과 트랜잭션 경계를 이 클래스 하나가 갖는다. Redis hold 생성은 이 트랜잭션 밖에서 이미
 * 끝났고, 여기서 실패하면 호출자가 그 hold를 보상 해제한다.
 */
@Service
@RequiredArgsConstructor
public class CreatePendingOrderTransactionService {
    private final OrderRepository orderRepository;
    private final OrderCreator orderCreator;
    private final OrderHoldHistoryRecorder orderHoldHistoryRecorder;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * @return 만들어진 주문의 orderKey. 트랜잭션 밖에서 entity를 다시 만지지 않도록 필요한 값만 돌려준다 — lazy 연관을 트랜잭션 밖에서 읽는 경로를
     *     애초에 만들지 않는다.
     */
    @Transactional
    public String create(
            final Long memberId,
            final Long performanceId,
            final Duration holdDuration,
            final Hold hold,
            final List<PerformanceSeat> performanceSeats,
            final PerformanceSaleSnapshot saleSnapshot) {
        final Order order =
                orderRepository.save(
                        orderCreator.createPendingOrder(
                                memberId,
                                performanceId,
                                hold.holdKey(),
                                hold.expiresAt(),
                                performanceSeats,
                                saleSnapshot));
        final LocalDateTime startedAt = hold.startedAt(holdDuration);
        orderHoldHistoryRecorder.recordCreated(
                memberId,
                performanceId,
                hold.holdKey(),
                startedAt,
                hold.expiresAt(),
                performanceSeats);
        eventPublisher.publishEvent(
                new OrderStarted(
                        UUID.randomUUID(),
                        OrderStarted.SCHEMA_VERSION,
                        order.getId(),
                        memberId,
                        hold.holdKey(),
                        performanceSeatIds(performanceSeats),
                        startedAt.atZone(clock.getZone()).toInstant()));
        return order.getOrderKey();
    }

    private Set<Long> performanceSeatIds(final List<PerformanceSeat> performanceSeats) {
        return performanceSeats.stream()
                .map(PerformanceSeat::getId)
                .collect(Collectors.toUnmodifiableSet());
    }
}
