package com.ticket.core.app.order.query;

import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import com.ticket.core.domain.order.OrderRemainingTime;
import com.ticket.core.domain.order.model.OrderState;
import com.ticket.core.app.order.query.model.OrderStatusView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import com.ticket.core.app.support.validation.RequiredInput;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetOrderStatusUseCase {

    private final OrderReadRepository orderReadRepository;
    private final Clock clock;

    public record Input(String orderKey, Long memberId) {
        public Input {
            RequiredInput.notBlank(orderKey, "orderKey");
            RequiredInput.positiveId(memberId, "memberId");
        }
    }

    public record Output(
            String orderKey,
            OrderState status,
            LocalDateTime expiresAt,
            long remainingSeconds
    ) {
    }

    public Output execute(final Input input) {
        final OrderStatusView status = orderReadRepository.findStatus(input.orderKey(), input.memberId())
                .orElseThrow(() -> new CoreException(ErrorType.ORDER_NOT_OWNED));
        final long remainingSeconds = OrderRemainingTime.seconds(
                status.status(),
                status.expiresAt(),
                LocalDateTime.now(clock)
        );

        return new Output(status.orderKey(), status.status(), status.expiresAt(), remainingSeconds);
    }
}
