package com.ticket.booking.order.usecase;

import static com.ticket.shared.api.InputChecks.requirePositiveId;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.booking.exception.OrderNotOwnedException;
import com.ticket.booking.order.domain.Order;
import com.ticket.booking.order.domain.OrderRemainingTime;
import com.ticket.booking.order.domain.OrderRepository;
import com.ticket.booking.order.domain.OrderState;
import com.ticket.shared.exception.InvalidRequestException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetOrderStatusUseCase {
    private final OrderRepository orderRepository;
    private final Clock clock;

    public record Input(String orderKey, Long memberId) {
        public Input {
            if (orderKey == null || orderKey.isBlank()) {
                throw new InvalidRequestException("orderKey는 필수입니다.");
            }
            memberId = requirePositiveId(memberId, "memberId");
        }
    }

    public record Output(String orderKey, OrderState status, LocalDateTime expiresAt, long remainingSeconds) {}

    public Output execute(final Input input) {
        final Order order = orderRepository
                .findByOrderKeyAndMemberId(input.orderKey(), input.memberId())
                .orElseThrow(() -> new OrderNotOwnedException(input.orderKey(), input.memberId()));
        final long remainingSeconds =
                OrderRemainingTime.seconds(order.getStatus(), order.getExpiresAt(), LocalDateTime.now(clock));

        return new Output(order.getOrderKey(), order.getStatus(), order.getExpiresAt(), remainingSeconds);
    }
}
