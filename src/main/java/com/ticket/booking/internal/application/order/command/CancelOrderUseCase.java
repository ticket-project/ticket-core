package com.ticket.booking.internal.application.order.command;

import com.ticket.error.InvalidRequestException;
import com.ticket.identity.MemberLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


/**
 * 회원 활성 확인(identity 공개 API)은 booking 쓰기 트랜잭션 밖에서 먼저 수행한다. 다른 module
 * 호출이 booking 트랜잭션 안에 있으면 그 module의 지연이나 실패가 booking connection을 붙잡는다
 * ({@link CreateOrderValidator}와 같은 이유). booking local 취소 처리는 {@link CancelOrderTransactionService}의
 * 짧은 쓰기 트랜잭션에서 수행한다.
 */
@Service
@RequiredArgsConstructor
public class CancelOrderUseCase {

    private final MemberLookup memberLookup;
    private final CancelOrderTransactionService cancelOrderTransactionService;

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

    public void execute(final Input input) {
        memberLookup.requireActive(input.memberId());
        cancelOrderTransactionService.cancel(input.orderKey(), input.memberId());
    }
}
