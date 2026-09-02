package com.ticket.booking.internal.application.order.query;

import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import com.ticket.booking.internal.domain.order.OrderRemainingTime;
import com.ticket.booking.internal.domain.order.model.OrderState;
import com.ticket.booking.internal.application.order.query.model.OrderStatusView;
import com.ticket.identity.MemberLookup;
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
    private final MemberLookup memberLookup;
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
        // 탈퇴한 회원은 자신의 주문 상태도 조회할 수 없다 — 기존에는 findStatus의 member join이
        // deletedAt으로 걸러냈다. member 조회가 booking 밖으로 빠졌으므로 여기서 같은 결과를 낸다.
        requireActiveMember(input.memberId());

        final long remainingSeconds = OrderRemainingTime.seconds(
                status.status(),
                status.expiresAt(),
                LocalDateTime.now(clock)
        );

        return new Output(status.orderKey(), status.status(), status.expiresAt(), remainingSeconds);
    }

    private void requireActiveMember(final Long memberId) {
        try {
            memberLookup.requireActive(memberId);
        } catch (final CoreException e) {
            if (e.getErrorType() == ErrorType.NOT_FOUND_DATA) {
                throw new CoreException(ErrorType.ORDER_NOT_OWNED);
            }
            throw e;
        }
    }
}
