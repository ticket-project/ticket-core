package com.ticket.booking.application;

import com.ticket.booking.OrderTerminated;
import com.ticket.booking.domain.HoldHistoryRecorder;
import com.ticket.booking.domain.Order;
import com.ticket.booking.domain.OrderSeat;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 좌석은 Order aggregate가 직접 들고 있으므로 별도 조회 없이 {@link Order#getOrderSeats()}로 읽는다.
 * 옛 구현은 {@code OrderSeatRepository}로 다시 조회한 뒤 "다른 주문의 좌석이 섞였는지"를 방어
 * 검사했지만, 컬렉션은 정의상 그 주문의 좌석만 담으므로 그 검사가 성립할 수 없어 함께 사라졌다.
 */
@Component
@RequiredArgsConstructor
public class OrderTerminationService {

    private final HoldHistoryRecorder holdHistoryRecorder;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public void cancel(final Order order, final LocalDateTime now) {
        final List<OrderSeat> orderSeats = order.getOrderSeats();
        order.cancel(now);
        holdHistoryRecorder.recordCanceled(
                order.getMemberId(), order.getPerformanceId(), order.getHoldKey(), now, orderSeats
        );
        publishTerminated(order, orderSeats, now);
    }

    public void expire(final Order order, final LocalDateTime now) {
        final List<OrderSeat> orderSeats = order.getOrderSeats();
        order.expire(now);
        holdHistoryRecorder.recordExpired(
                order.getMemberId(), order.getPerformanceId(), order.getHoldKey(), now, orderSeats
        );
        publishTerminated(order, orderSeats, now);
    }

    private void publishTerminated(final Order order, final List<OrderSeat> orderSeats, final LocalDateTime now) {
        eventPublisher.publishEvent(new OrderTerminated(
                UUID.randomUUID(),
                OrderTerminated.SCHEMA_VERSION,
                order.getId(),
                order.getMemberId(),
                order.getHoldKey(),
                performanceSeatIds(orderSeats),
                order.getStatus().name(),
                now.atZone(clock.getZone()).toInstant()
        ));
    }

    private Set<Long> performanceSeatIds(final List<OrderSeat> orderSeats) {
        return orderSeats.stream()
                .map(OrderSeat::getPerformanceSeatId)
                .collect(Collectors.toUnmodifiableSet());
    }
}
