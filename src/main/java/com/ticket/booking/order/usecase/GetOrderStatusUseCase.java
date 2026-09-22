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
import com.ticket.member.api.MemberLookupApi;
import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.shared.exception.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetOrderStatusUseCase {
    private final OrderRepository orderRepository;
    private final MemberLookupApi memberLookupApi;
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
        // 탈퇴한 회원은 자신의 주문 상태도 조회할 수 없다 — 기존에는 상태 조회의 member join이
        // deletedAt으로 걸러냈다. member 조회가 booking 밖으로 빠졌으므로 여기서 같은 결과를 낸다.
        requireActiveMember(input.orderKey(), input.memberId());

        final long remainingSeconds =
                OrderRemainingTime.seconds(order.getStatus(), order.getExpiresAt(), LocalDateTime.now(clock));

        return new Output(order.getOrderKey(), order.getStatus(), order.getExpiresAt(), remainingSeconds);
    }

    private void requireActiveMember(final String orderKey, final Long memberId) {
        try {
            memberLookupApi.requireActive(memberId);
        } catch (final NotFoundException e) {
            throw new OrderNotOwnedException(orderKey, memberId);
        }
    }
}
