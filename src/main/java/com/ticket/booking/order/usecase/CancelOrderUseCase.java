package com.ticket.booking.order.usecase;

import static com.ticket.shared.api.InputChecks.requirePositiveId;

import org.springframework.stereotype.Service;

import com.ticket.shared.exception.InvalidRequestException;

import lombok.RequiredArgsConstructor;

/** booking local 취소 처리는 {@link CancelOrderTransactionService}의 짧은 쓰기 트랜잭션에서 수행한다. */
@Service
@RequiredArgsConstructor
public class CancelOrderUseCase {
    private final CancelOrderTransactionService cancelOrderTransactionService;

    public record Input(String orderKey, Long memberId) {
        public Input {
            if (orderKey == null || orderKey.isBlank()) {
                throw new InvalidRequestException("orderKey는 필수입니다.");
            }
            memberId = requirePositiveId(memberId, "memberId");
        }
    }

    public void execute(final Input input) {
        cancelOrderTransactionService.cancel(input.orderKey(), input.memberId());
    }
}
