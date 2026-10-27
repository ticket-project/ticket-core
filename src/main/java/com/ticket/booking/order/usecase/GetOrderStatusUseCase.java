package com.ticket.booking.order.usecase;

import static com.ticket.shared.api.InputChecks.requirePositiveId;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.booking.exception.BookingErrorCode;
import com.ticket.booking.exception.BookingException;
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
                .orElseThrow(() -> new BookingException(BookingErrorCode.E5003));
        // 만료 시각이 지난 PENDING은 청소가 DB를 바꾸기 전이어도 EXPIRED로 보인다(주문 유효성은 만료 시각이 정한다).
        final LocalDateTime now = LocalDateTime.now(clock);
        final OrderState status = order.effectiveStatus(now);
        final long remainingSeconds = OrderRemainingTime.seconds(status, order.getExpiresAt(), now);

        return new Output(order.getOrderKey(), status, order.getExpiresAt(), remainingSeconds);
    }
}
