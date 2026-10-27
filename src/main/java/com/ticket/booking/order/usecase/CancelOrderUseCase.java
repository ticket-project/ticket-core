package com.ticket.booking.order.usecase;

import static com.ticket.shared.api.InputChecks.requirePositiveId;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.booking.exception.BookingErrorCode;
import com.ticket.booking.exception.BookingException;
import com.ticket.booking.order.domain.Order;
import com.ticket.booking.order.domain.OrderRepository;
import com.ticket.booking.order.domain.OrderState;
import com.ticket.shared.exception.InvalidRequestException;

import lombok.RequiredArgsConstructor;

/** booking local 주문 조회와 취소를 하나의 짧은 쓰기 트랜잭션에서 수행한다. */
@Service
@RequiredArgsConstructor
public class CancelOrderUseCase {
    private final OrderRepository orderRepository;
    private final OrderTerminationService orderTerminationService;
    private final Clock clock;

    public record Input(String orderKey, Long memberId) {
        public Input {
            if (orderKey == null || orderKey.isBlank()) {
                throw new InvalidRequestException("orderKey는 필수입니다.");
            }
            memberId = requirePositiveId(memberId, "memberId");
        }
    }

    @Transactional
    public void execute(final Input input) {
        final Order order = orderRepository
                .findByOrderKeyAndMemberIdForUpdate(input.orderKey(), input.memberId())
                .orElseThrow(() -> new BookingException(BookingErrorCode.E5003));
        // 만료 시각이 지난 PENDING은 이미 끝난 주문이다. 취소로 바꾸지 않고 청소가 EXPIRED로 바꾼다.
        final LocalDateTime now = LocalDateTime.now(clock);
        if (order.effectiveStatus(now) != OrderState.PENDING) {
            throw new BookingException(BookingErrorCode.E5002);
        }
        orderTerminationService.cancel(order, now);
    }
}
