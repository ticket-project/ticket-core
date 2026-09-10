package com.ticket.booking.order.application.usecase;

import com.ticket.booking.order.application.port.OrderQueryPort;

import com.ticket.booking.order.domain.OrderRemainingTime;
import com.ticket.booking.order.domain.OrderState;
import com.ticket.booking.order.application.OrderStatusView;
import com.ticket.booking.order.exception.OrderNotOwnedException;
import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.shared.exception.NotFoundException;
import com.ticket.member.MemberLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetOrderStatusUseCase {

    private final OrderQueryPort orderQueryPort;
    private final MemberLookup memberLookup;
    private final Clock clock;

    public record Input(String orderKey, Long memberId) {
        public Input {
            if (orderKey == null || orderKey.isBlank()) {
                throw new InvalidRequestException("orderKey는 필수입니다.");
            }
            if (memberId == null) {
                throw new InvalidRequestException("memberId는 필수입니다.");
            }
            if (memberId <= 0) {
                throw new InvalidRequestException("memberId는 양수여야 합니다.");
            }
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
        final OrderStatusView status = orderQueryPort.findStatus(input.orderKey(), input.memberId())
                .orElseThrow(() -> new OrderNotOwnedException(input.orderKey(), input.memberId()));
        // 탈퇴한 회원은 자신의 주문 상태도 조회할 수 없다 — 기존에는 findStatus의 member join이
        // deletedAt으로 걸러냈다. member 조회가 booking 밖으로 빠졌으므로 여기서 같은 결과를 낸다.
        requireActiveMember(input.orderKey(), input.memberId());

        final long remainingSeconds = OrderRemainingTime.seconds(
                status.status(),
                status.expiresAt(),
                LocalDateTime.now(clock)
        );

        return new Output(status.orderKey(), status.status(), status.expiresAt(), remainingSeconds);
    }

    private void requireActiveMember(final String orderKey, final Long memberId) {
        try {
            memberLookup.requireActive(memberId);
        } catch (final NotFoundException e) {
            throw new OrderNotOwnedException(orderKey, memberId);
        }
    }
}
